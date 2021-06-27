package com.schlaikjer.music.utility;

import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.schlaikjer.msgs.TrackOuterClass;
import com.schlaikjer.music.model.NetworkOpcode;
import com.schlaikjer.music.model.Packet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NetworkManager {

    private static final String TAG = NetworkManager.class.getSimpleName();

    static final String SERVER_HOST = "rhye.org";
    // static final String SERVER_HOST = "192.168.0.41";
    static final int SERVER_PORT = 5959;

    // How long to keep the socket connected with no active transactions
    static final long SOCKET_KEEPALIVE_MS = 15_000;

    // Content database fetch callback
    public interface DatabaseFetchCallback {
        void onDatabaseFetched(TrackOuterClass.MusicDatabase db);

        void onAbort();
    }

    // Callback for fetchTrack / fetchImage.
    public interface ContentFetchCallback {
        void onContentReceived(byte[] data);

        void onAbort();
    }

    // Callback for requesting a rescan of the remote DB
    // Returns once the DB update is _requested_, not after it is _completed_
    public interface DatabaseRescanCallback {
        void onSuccess();

        void onAbort();
    }

    // Internal callback wrapper
    private interface TxnCalllback {
        void onSuccess(Packet p);

        void onAbort();
    }

    // Has the manager been initialized
    private static boolean initialized = false;
    // Background thread for networking IO
    private static Thread _network_thread;
    // Socket handle
    private static Socket _server_socket = null;
    // Timeout counter for disconnecting the socket
    private static long _socket_last_activity = 0;
    // Internal callback map
    private static final Map<Integer, TxnCalllback> _callbacks = new HashMap<>();
    private static final Set<byte[]> _active_content_requests = new HashSet<>();
    private static final Map<byte[], List<ContentFetchCallback>> _chained_content_request_callbacks = new HashMap<>();
    // Transmit queue of pending requests
    private static final LinkedList<Packet> _packet_tx_queue = new LinkedList<>();
    // Receive queue
    private static final ByteArrayOutputStream _incoming_data_slab = new ByteArrayOutputStream();

    // Nonce management
    private static final int NO_NONCE = 0xFFFFFFFF;
    private static int _nonce_counter = 0;

    private static int nextNonce() {
        while (++_nonce_counter == NO_NONCE) ;
        return _nonce_counter;
    }

    public static void init() {
        if (initialized) {
            return;
        }

        synchronized (NetworkManager.class) {
            if (initialized) {
                return;
            }

            // Create network IO thread
            _network_thread = new Thread(NetworkManager::networkLoop);
            _network_thread.start();

            // We are now initialized
            initialized = true;
            Log.d(TAG, "Network thread initialized");
        }
    }

    private static void reset_connection() {
        // Disconnect socket
        _server_socket = null;


        // Abort any pending callbacks
        synchronized (_packet_tx_queue) {
            for (TxnCalllback callback : _callbacks.values()) {
                callback.onAbort();
            }
            _callbacks.clear();
            _active_content_requests.clear();
        }
    }

    private static void networkLoop() {
        Log.d(TAG, "Starting network loop");
        while (true) {
            // If there are no outstanding networking operations, check to see if the connection has been idle long enough to be worth closing
            if (_server_socket != null && _callbacks.size() == 0 && _packet_tx_queue.size() == 0) {
                if (System.currentTimeMillis() > _socket_last_activity + SOCKET_KEEPALIVE_MS) {
                    // Socket is in timeout, close it down
                    Log.d(TAG, "Connection timeout reached, closing socket");
                    reset_connection();
                }
            }

            // If socket is live, check for incoming data
            boolean did_rx_new_data = false;
            try {
                byte[] buffer = new byte[1024];
                while (_server_socket != null && _server_socket.getInputStream().available() > 0) {
                    // Update activity counter
                    _socket_last_activity = System.currentTimeMillis();

                    // Ingest data from socket
                    int read =
                            _server_socket.getInputStream().read(buffer, 0, buffer.length);
                    _incoming_data_slab.write(buffer, 0, read);
                    did_rx_new_data = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                reset_connection();
                continue;
            }

            try {
                // Attempt to deserialize packets out of the buffered incoming data
                while (did_rx_new_data && _incoming_data_slab.size() >= 12) {

                    // If there are at least enough bytes for a header in the rx buffer, test to see if we can resolve an incoming packet
                    Packet p = Packet.deserialize(_incoming_data_slab);
                    if (p != null) {
                        synchronized (_packet_tx_queue) {
                            TxnCalllback callback = _callbacks.get(p.nonce);
                            _callbacks.remove(p.nonce);
                            Log.d(TAG, "Resolved callback with nonce " + p.nonce);
                            if (callback != null) {
                                callback.onSuccess(p);
                            }
                        }
                    } else {
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                reset_connection();
                continue;
            }

            // If there are incoming data to be sent, handle them
            try {
                synchronized (_packet_tx_queue) {
                    while (!_packet_tx_queue.isEmpty()) {
                        // Connect socket if not already connected
                        if (!ensureConnected()) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            break;
                        }

                        // Update activity counter
                        _socket_last_activity = System.currentTimeMillis();

                        // Serialize the head packet
                        Packet p = _packet_tx_queue.pop();
                        Log.d(TAG, "Sending packet with nonce " + p.nonce);
                        p.serialize(_server_socket.getOutputStream());
                        Log.d(TAG, "Packet tx queue now " + _packet_tx_queue.size());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                reset_connection();
                continue;
            }

            // Hideous. Please, just let me select()
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean ensureConnected() {
        // Is the socket initialized?
        if (_server_socket == null) {
            try {
                _server_socket = new Socket(SERVER_HOST, SERVER_PORT);
                _server_socket.setKeepAlive(true);
                _socket_last_activity = System.currentTimeMillis();
                Log.d(TAG, "Created new server socket");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return _server_socket.isConnected();
    }

    public static void fetchDatabase(DatabaseFetchCallback cb) {
        init();

        // Wrap packet
        Packet packet = new Packet(nextNonce(), NetworkOpcode.FETCH_DB, new byte[0]);

        // Lock tx queue
        synchronized (_packet_tx_queue) {
            // Queue packet to be sent
            _packet_tx_queue.add(packet);

            // Put the callback handler in the map
            _callbacks.put(packet.nonce, new TxnCalllback() {
                @Override
                public void onSuccess(Packet p) {
                    if (p.opcode != NetworkOpcode.FETCH_DB) {
                        Log.e(TAG, "Unexpected return opcode for fetch db call - wanted " + NetworkOpcode.FETCH_DB + " got " + p.opcode);
                        cb.onAbort();
                        return;
                    }

                    try {
                        TrackOuterClass.MusicDatabase db = TrackOuterClass.MusicDatabase.parseFrom(p.data);
                        cb.onDatabaseFetched(db);
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                        cb.onAbort();
                    }
                }

                @Override
                public void onAbort() {
                    cb.onAbort();
                }
            });
        }
    }

    public static void fetchTrack(byte[] checksum, ContentFetchCallback cb) {
        fetchContent(checksum, cb, NetworkOpcode.FETCH_TRACK);
    }

    public static void fetchImage(byte[] checksum, ContentFetchCallback cb) {
        fetchContent(checksum, cb, NetworkOpcode.FETCH_IMAGE);
    }

    private static void fetchContent(byte[] checksum, ContentFetchCallback cb, int op) {
        if (checksum == null) {
            cb.onAbort();
            return;
        }

        init();

        Packet packet = new Packet(nextNonce(), op, checksum);

        // Lock tx queue
        synchronized (_packet_tx_queue) {
            // Queue this callback to piggy-back the existing request
            if (!_chained_content_request_callbacks.containsKey(checksum)) {
                _chained_content_request_callbacks.put(checksum, new ArrayList<>());
            }
            _chained_content_request_callbacks.get(checksum).add(cb);

            // Do we already have an active request for this content ID?
            if (_active_content_requests.contains(checksum)) {
                return;
            }

            // Add this content ID to the active request set
            _active_content_requests.add(checksum);

            // Add our proxy content callback callback to the main callback map
            _callbacks.put(packet.nonce, new TxnCalllback() {
                @Override
                public void onSuccess(Packet p) {
                    // Extract the list of callbacks for this content ID
                    List<ContentFetchCallback> cbs;
                    synchronized (_packet_tx_queue) {
                        cbs = _chained_content_request_callbacks.get(checksum);
                        _chained_content_request_callbacks.remove(checksum);
                    }

                    // Check response packet is good
                    if (p.opcode != op) {
                        // If it isn't, abort all pending callbacks
                        Log.e(TAG, "Unexpected return opcode for call - wanted " + op + " got " + p.opcode);
                        for (ContentFetchCallback cb : cbs) {
                            cb.onAbort();
                        }
                        return;
                    }

                    // Invoke success callbacks
                    for (ContentFetchCallback cb : cbs) {
                        cb.onContentReceived(p.data);
                    }
                    _chained_content_request_callbacks.remove(checksum);
                }

                @Override
                public void onAbort() {
                    // Extract the list of callbacks for this content ID
                    List<ContentFetchCallback> cbs;
                    synchronized (_packet_tx_queue) {
                        cbs = _chained_content_request_callbacks.get(checksum);
                        _chained_content_request_callbacks.remove(checksum);
                    }

                    for (ContentFetchCallback cb : cbs) {
                        cb.onAbort();
                    }
                }
            });

            // Queue packet to be sent
            _packet_tx_queue.add(packet);
        }
    }

    public static void rescanDatabase(DatabaseRescanCallback cb) {
        init();

        // Wrap packet
        Packet packet = new Packet(nextNonce(), NetworkOpcode.UPDATE_REMOTE_DB, new byte[0]);

        // Lock tx queue
        synchronized (_packet_tx_queue) {
            // Queue packet to be sent
            _packet_tx_queue.add(packet);

            // Put the callback handler in the map
            _callbacks.put(packet.nonce, new TxnCalllback() {
                @Override
                public void onSuccess(Packet p) {
                    if (p.opcode != NetworkOpcode.UPDATE_REMOTE_DB) {
                        Log.e(TAG, "Unexpected return opcode for rescan db call - wanted " + NetworkOpcode.FETCH_DB + " got " + p.opcode);
                        cb.onAbort();
                        return;
                    }

                    cb.onSuccess();
                }

                @Override
                public void onAbort() {
                    cb.onAbort();
                }
            });
        }
    }

}

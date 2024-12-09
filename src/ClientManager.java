import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Vector;

public class ClientManager {
    private String serverAddress;
    private int serverPort;
    private Client client;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread receiveThread;

    private GamePanel gamePanel;

    private User user;
    private String userName;
    private Vector<String> userNames = new Vector<>();

    public ClientManager(String serverAddress, int serverPort, Client client) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.client = client;
    }

    public void connectToServer() throws IOException {
        socket = new Socket();
        SocketAddress sa = new InetSocketAddress(serverAddress, serverPort);
        socket.connect(sa, 3000);

        out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.flush();
        in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

        receiveThread = new Thread(this::run);
        receiveThread.start();
    }

    private void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                receiveMessage();
            }
        } catch (Exception e) {
            System.err.println("메시지 수신 중 오류 발생: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void receiveMessage() {
        try {
            GameMsg inMsg = (GameMsg) in.readObject();
//            user = inMsg.getUser();

            if (user == null) {
                System.err.println("클라이언트에서 받은 User 객체가 null입니다.");
            } else {
                System.out.println("User currentRoom: " +user.getName() + ", 현재 방 : " + user.getCurrentRoom()); // currentRoom 확인
            }

            if (inMsg == null) {
                disconnect();
                System.err.println("receiveMessage 서버 연결 끊김");
                return;
            }
            SwingUtilities.invokeLater(() -> {
                switch (inMsg.mode) {
                    case GameMsg.LOGIN_OK:
                        user = inMsg.getUser(); // user 여기에서 저장해야 유지됨
                        client.changeSelectRoomPanel();
                        break;
                    case GameMsg.ROOM_SELECT_OK:
                        System.out.println("클라이언트 receiveMessage : " + inMsg.mode + "," + inMsg.user.name + "," + inMsg.message);
                        client.changeGameRoomPanel(inMsg);
                        client.getGamePanel().clearLines();
                        break;
                    case GameMsg.ROOM_NEW_MEMBER:
                        System.out.println("새로운 유저 >" + inMsg.user.name + "가 들어옴");
                        System.out.println("추가되기 전 userNames : " + userNames);
                        // 새로들어온 유저의 user.getCurrentRoom.getMembers를 userNames에 넣어. 그러고 client.업데이트함수 불러서 그걸로 userData 업데이트하게해
                        if (!userNames.contains(inMsg.user.name)) { // 목록에 없는 유저가 들어올 때만 리프레쉬
                            userNames.add(inMsg.user.name);
                            System.out.println("추가된 후 userNames : " + userNames);
                            client.updateUserToRoom(userNames);
                        }
                        break;
                    //채팅 모드 등...
                    case GameMsg.CHAT_MESSAGE:
                        System.out.println("receiveMessage 서버로부터 메시지 수신: ");
                        break;
                    case GameMsg.DRAW_ACTION:
                        Paint paintData = inMsg.getPaintData();
                        client.getGamePanel().receiveRemoteDrawing(
                                paintData.getStartX(),
                                paintData.getStartY(),
                                paintData.getEndX(),
                                paintData.getEndY(),
                                paintData.getColor()
                        );

                        break;
                    //이모티콘 전송 모드 등...
//                case GameMsg.MODE_TX_IMAGE :
//                    printDisplay(inMsg.userID + ": " + inMsg.message);
//                    printDisplay(inMsg.image);
//                    break;
                }
            });
        } catch (IOException e) {
            System.err.println("receiveMessage 서버 연결 종료: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void disconnect() {
        sendGameMsg(new GameMsg(GameMsg.LOGOUT, userName));
        try {
            receiveThread = null;
            socket.close();
        } catch (IOException e) {
            System.err.println("클라이언트 disconnect 닫기 오류> "+e.getMessage());
            System.exit(-1);
        }
    }

    void sendGameMsg(GameMsg msg) {
        try {
            if (out != null) {
                out.writeObject(msg); // 객체 전송
                out.flush();
            } else {
                System.err.println("sendGameMsg 출력 스트림이 초기화되지 않았습니다.");
            }
        } catch (IOException e) {
            System.err.println("클라이언트 sendGameMsg 전송 오류: " + e.getMessage());
        }
    }

    public void sendNickname(String nickname) {
        this.userName = nickname;
        sendGameMsg(new GameMsg(GameMsg.LOGIN, userName));
    }

    public void sendRoomSelection(String roomName) {
        sendGameMsg(new GameMsg(GameMsg.ROOM_SELECT, user, roomName));
    }

    public void sendDrawingData(int startX, int startY, int endX, int endY, Color color,  boolean isErasing) {
        if (out == null) {
            System.err.println("출력 스트림이 초기화되지 않았습니다. 데이터를 전송할 수 없습니다.");
            return;
        }
        try {
            Color defaultColor = Color.BLACK;
            // 현재 사용자의 정보와 방 정보를 포함하여 메시지 생성
            Paint paintData = new Paint(startX, startY, endX, endY, color, isErasing);
            GameMsg msg = new GameMsg(GameMsg.DRAW_ACTION, paintData);
            sendGameMsg(msg);

            System.out.println(String.format(
                    "클라이언트 전송 - 시작(%d, %d), 끝(%d, %d), 색상: R:%d, G:%d, B:%d",
                    startX, startY, endX, endY,
                    //color.getRed(), color.getGreen(), color.getBlue()
                    defaultColor.getRed(), defaultColor.getGreen(), defaultColor.getBlue()
            ));
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

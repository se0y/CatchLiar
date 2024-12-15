import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.Vector;

public class ServerManager {
    private int port;
    private Server server;

    private ServerSocket serverSocket;
    private Thread acceptThread = null;
    private Vector<ClientHandler> users = new Vector<ClientHandler>();
    private Vector<Room> rooms = new Vector<>();

    private static final int DRAWING_TIME=12;
    private static final int DRAWING_PERTIME=DRAWING_TIME/4;
    private static final int VOTE_TIME=10;
//    private Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public ServerManager(int port, Server server) {
        this.port = port;
        this.server = server;
    }

    public void startServer() {
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                server.printDisplay("서버가 시작되었습니다. 포트: " + port);

                while (acceptThread == Thread.currentThread()) {
                    Socket clientSocket = serverSocket.accept();
                    server.printDisplay("클라이언트가 연결되었습니다: " + clientSocket.getInetAddress().getHostAddress());

                    ClientHandler handler = new ClientHandler(clientSocket);
                    users.add(handler);
                    handler.start();
                }
            } catch (IOException e) {
                server.printDisplay("서버 소켓 종료: " + e.getMessage());
            } finally {
                stopServer();
            }
        });
        acceptThread.start();
    }

    public void stopServer() {
        try {
            if (serverSocket != null) serverSocket.close();
            acceptThread = null;
            server.printDisplay("서버가 종료되었습니다.");
        } catch (IOException e) {
            server.printDisplay("서버 종료 중 오류: " + e.getMessage());
        }
    }

    public void exit() {
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            System.err.println("서버 닫기 오류> " + e.getMessage());
        }
        System.exit(-1);
    }


    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private User user;
        public String userName;
        private Room currentRoom = null;
        public boolean isLiar = false;
        private Vector<User> readyUsers = new Vector<>();
        public User liar;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        // 그림 데이터를 처리하는 메서드 추가
        private void
        handleDrawAction(GameMsg inMsg) {
            Paint paintData = inMsg.getPaintData();
            Color color = inMsg.getPaintData().getColor() != null ? inMsg.getPaintData().getColor() : Color.BLACK;

            //드로잉 확인 패널
//            server.printDisplay("DRAW_ACTION 수신: 시작(" + paintData.getStartX() + ", " + paintData.getStartY() +
//                    "), 끝(" + paintData.getEndX() + ", " + paintData.getEndY() + "), 색상: " + paintData.getColor() +
//                    ", 지우개 모드: " + paintData.isErasing());

            broadcasting(new GameMsg(GameMsg.DRAW_ACTION, paintData)); // 그림 데이터를 다른 클라이언트들에게 전송
        }

        private void receiveMessage(Socket cs) {
            try {
                in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                out = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
                out.flush();

                GameMsg inMsg;
                while ((inMsg = (GameMsg) in.readObject()) != null) {
                    switch (inMsg.getMode()) {
                        case GameMsg.LOGIN:
                            user = inMsg.getUser();
                            userName = user.name;
                            server.printDisplay("새 참가자: " + userName);
                            sendGameMsg(new GameMsg(GameMsg.LOGIN_OK, user));
                            break;

                        case GameMsg.ROOM_SELECT:
                            user = inMsg.user;
                            enterRoom(inMsg.getMsg());
                            user.setCurrentRoom(currentRoom);
                            user.joinRoom(currentRoom);
//                            user = inMsg.user;
                            if(user.currentRoom.getMemberCount() > 4) {
                                user.leaveRoom();
                                sendGameMsg(new GameMsg(GameMsg.ROOM_SELECT_DENIED, user));
                                server.printDisplay(userName + "님이 " + inMsg.getMsg() + "방에 입장하지 못했습니다.");
                                break;
                            }
                            server.printDisplay(userName + "님이 방 [" + user.getCurrentRoom().getRoomName() + "]에 입장했습니다. 현재 : " + user.currentRoom.getMemberCount() + "명");
                            // user.currentRoom. 키워드 세팅
                            sendGameMsg(new GameMsg(GameMsg.ROOM_SELECT, user, inMsg.getMsg()));
                            broadcasting(new GameMsg(GameMsg.ROOM_NEW_MEMBER, user, inMsg.getMsg())); // currentRoom

                            // 4명 다 들어오면 준비 가능하도록
                            if(user.currentRoom.getMemberCount() == 4) {
                                broadcasting(new GameMsg(GameMsg.GAME_READY_AVAILABLE));
                            }
                            break;

                        case GameMsg.CHAT_MESSAGE:
                            user = inMsg.user;
                            broadcasting(new GameMsg(GameMsg.CHAT_MESSAGE, user, inMsg.getMsg()));
                            server.printDisplay(user.currentRoom.getRoomName() + "에서 " + inMsg.user.name + "님 채팅 : " + inMsg.getMsg());
                            break;

                        case GameMsg.CHAT_EMOTICON:
//                            user = inMsg.user;
                            broadcasting(new GameMsg(GameMsg.CHAT_EMOTICON, user, inMsg.getMsg()));
                            server.printDisplay(user.currentRoom.getRoomName() + "에서 " + inMsg.user.name + "님이 " + inMsg.getMsg() + "이모티콘 전송");
                            break;

                        case GameMsg.GAME_READY:
//                            inMsg.user.setReady();
                            server.printDisplay(user+"님이 준비 완료");
                            user.setReady();
                            broadcasting(new GameMsg(GameMsg.GAME_READY_OK, user));
                            break;

                        case GameMsg.GAME_UN_READY:
//                            inMsg.user.setUnReady();
                            server.printDisplay(user+"님이 준비 해제");
                            user.setUnReady();
                            broadcasting(new GameMsg(GameMsg.GAME_UN_READY_OK, user));
                            break;

                        case GameMsg.GAME_START:
                            readyUsers = inMsg.readyUsers;
                            liar = selectLiar(inMsg.readyUsers);
                            liar.isLiar = true;
                            if(liar == null) {
                                System.out.println("라이어가 뽑히지 않았습니다.");
                                server.printDisplay("에러 : 라이어가 뽑히지 않았습니다.");
                            } else {
                                System.out.println("뽑힌 라이어 이름 : " + liar.name);
                                server.printDisplay("뽑힌 라이어 이름 : " + liar.name);
                            }
                            currentRoom.setMembers(inMsg.userNames);
                            //턴 초기화
                            currentRoom.resetTurns();
                            System.out.println("setMembers 함 : " + currentRoom.getMembers());

                            broadcastIndividualUser(liar, new GameMsg(GameMsg.LIAR_NOTIFICATION, liar));
                            broadcastExceptUser(liar, new GameMsg(GameMsg.KEYWORD_NOTIFICATION, user, user.currentRoom.getKeyword()));

                            // 타이머 시작
                            server.printDisplay("타이머 시작");
                            startRoomTimer(currentRoom, DRAWING_TIME);
                            break;

                        case GameMsg.VOTE:
                            String votedUser = inMsg.getMsg();
                            if (votedUser != null) {
                                server.printDisplay(userName + "이 " + votedUser + "에게 투표했습니다.");
                                currentRoom.addVote(votedUser);
                            } else {
                                server.printDisplay("투표 값이 null입니다.");
                            }
                            break;

                        case GameMsg.DRAW_ACTION:
                            handleDrawAction(inMsg);
                            break;

                        case GameMsg.ROOM_EXIT:
//                            user = inMsg.user;
                            broadcasting(new GameMsg(GameMsg.ROOM_EXIT, inMsg.user, "finish"));
                            user.leaveRoom(); // 안 하면, 방 관리는 되는데 순서관리가 안됨

//                            exitRoom();

                            server.printDisplay(userName + "님이 " + currentRoom.getRoomName() + "방을 나갔습니다. 현재 인원 : " + currentRoom.getMemberCount());
//                            currentRoom = null;
                            break;

                        case GameMsg.LOGOUT:
                            broadcasting(new GameMsg(GameMsg.LOGOUT, inMsg.user));
                            user.leaveRoom();
                            server.printDisplay(userName + "님이 로그아웃했습니다.");
                            break;

                        default:
                            server.printDisplay("서버 receiveMessage 알 수 없는 메시지 모드: " + inMsg.getMode());
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                disconnectClient();
                server.printDisplay("서버 receiveMessage 클라이언트 연결 해제: " + e.getMessage());
                broadcasting(new GameMsg(GameMsg.LOGOUT, user));
            } finally {
                disconnectClient();
            }
        }

        private void sendGameMsg(GameMsg msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                System.out.println("서버 sendGameMsg 전송 오류>" + e.getMessage());
                e.printStackTrace();
            }

        }

        private void startRoomTimer(Room room, int totalTime) {
            new Thread(() -> {
                int remainingTime = totalTime;
                int turns = 0; // 현재 턴 횟수
                int totalTurns = room.getMembers().size(); // 총 턴 횟수
                int turnTimeRemaining = DRAWING_PERTIME; // 각 턴의 남은 시간 초기화
                //User lastTurnUser = null;
                try {
                    // 첫 번째 사용자 알림
                    room.nextTurn(); // 첫 사용자 설정
                    User currentUser = room.getCurrentTurnUser();
                    if (currentUser != null) {
                        GameMsg firstTurnMsg = new GameMsg(GameMsg.TIME, currentUser, "Your turn!", remainingTime, room.getMembers());
                        broadcasting(firstTurnMsg);
                        server.printDisplay("현재 턴: " + currentUser.getName());
                    }

                    while (remainingTime > 0) {
                        Thread.sleep(1000); // 1초 간격으로 실행
                        remainingTime--;

                        turnTimeRemaining--; // 현재 턴의 남은 시간 감소
                        //User currentUser = room.getCurrentTurnUser();

                        // 현재 턴 사용자 확인
//                        if (remainingTime % DRAWING_PERTIME == 0 || remainingTime ==totalTime) { // 턴 전환
//                            room.nextTurn(); // 다음 사용자로 턴 전환
//                            User currentUser = room.getCurrentTurnUser();
//
//                            // 현재 턴 사용자 정보 브로드캐스트
//                            GameMsg turnMsg = new GameMsg(GameMsg.TIME, currentUser, "Your turn!", remainingTime, currentRoom.getMembers());
//                            broadcasting(turnMsg);
//
//
//                            //lastTurnUser = currentUser; // 마지막 턴 사용자 업데이트
//                            server.printDisplay("현재 턴: " + (currentUser != null ? currentUser.getName() : "없음"));
//                            System.out.println("현재 턴: " + (currentUser != null ? currentUser.getName() : "없음"));
//                        }

                        // 턴 종료 조건 확인
                        if (turnTimeRemaining <= 0 && remainingTime > 0) {
                            turnTimeRemaining = DRAWING_PERTIME; // 다음 턴 시간 초기화
                            room.nextTurn(); // 다음 사용자로 턴 전환
                            currentUser = room.getCurrentTurnUser();

                            // 다음 사용자 알림
                            if (currentUser != null) {
                                GameMsg turnMsg = new GameMsg(GameMsg.TIME, currentUser, "Your turn!", remainingTime, room.getMembers());
                                broadcasting(turnMsg);
                                server.printDisplay("현재 턴: " + currentUser.getName());
                            }
                        }

                        // TIME 메시지를 생성하여 브로드캐스트
                        GameMsg timeMsg = new GameMsg(GameMsg.TIME, null, null, remainingTime, currentRoom.getMembers());
                        broadcasting(timeMsg);
                    }

                    //시간 종료되면 투표 모드 전환
                    server.printDisplay("시간 종료!!");
                    // 투표 타이머:
                    GameMsg voteStartMsg = new GameMsg(GameMsg.VOTE, null, "투표를 시작하세요!", VOTE_TIME, currentRoom.getMembers());
                    voteStartMsg.setVoteStart(true); // 투표 시작 메시지로 설정
                    broadcasting(voteStartMsg);
                    // 투표 타이머 시작
                    startVoteTimer(room, VOTE_TIME); // 20초 동안 투표 실행
                    System.out.println("타이머 종료 - 방 [" + room.getRoomName() + "]");
                } catch (InterruptedException e) {
                    System.err.println("타이머 중단 - 방 [" + room.getRoomName() + "], 오류: " + e.getMessage());
                }
            }).start();
        }

        // 투표 타이머 실행
        private void startVoteTimer(Room room, int voteTime) {
            new Thread(() -> {
                int remainingTime = voteTime;
                try {
                    while (remainingTime > 0) {
                        Thread.sleep(1000);
                        remainingTime--;

                        // 타이머 메시지 전송
                        GameMsg voteTimeMsg = new GameMsg(GameMsg.VOTE, null, null, remainingTime, currentRoom.getMembers());
                        voteTimeMsg.setVoteStart(false); // 타이머 메시지
                        broadcasting(voteTimeMsg);
                    }

                    collectVoteResults(room);

                    // 투표 결과 집계
                    server.printDisplay("투표 타이머 끝");
//                    GameMsg gameEndMsg = new GameMsg(GameMsg.GAME_END, null, "게임종료");
//                    broadcasting(gameEndMsg);
                } catch (InterruptedException e) {
                    System.err.println("투표 타이머 중단 - 방 [" + room.getRoomName() + "], 오류: " + e.getMessage());
                }
            }).start();
        }

        //투표 결과 집계
        private void collectVoteResults(Room room) {
            Map<String, Integer> voteCounts = room.getVoteCounts();

            // 최다 득표자 계산
            String liarCandidate = voteCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .get()
                    .getKey();

            // 라이어 승리 여부 판단
            boolean liarVictory = !liarCandidate.equals(liar.name);

            // 서버 패널 표시용 ----------
            String resultMessage = liarVictory
                    ? "라이어가 승리했습니다! 라이어는 " + liar.name + "입니다."
                    : "라이어가 패배했습니다! " + liarCandidate + "님이 지목되었습니다.";

            // 라이어 결과 메시지 생성

            // 결과 메시지 작성
            //String liarWinMessage = "라이어가 승리했습니다! 라이어는 " + liar.name + "입니다.";
            //String liarLoseMessage = "라이어가 패배했습니다! " + liarCandidate + "님이 지목되었습니다.";

            String liarWinMessage = "라이어: " + liar.name ;
            String liarLoseMessage = "라이어: " + liar.name ;

            // 라이어에게 메시지 전송
            String liarResultMessage = liarVictory ? liarWinMessage : liarLoseMessage;
            System.out.println("[DEBUG] 라이어에게 전송할 메시지: " + liarResultMessage);
            System.out.println("[DEBUG] 라이어의 승리 여부: " + liarVictory);

            broadcastIndividualUser(
                    liar,
                    new GameMsg(GameMsg.GAME_END, liar, liarResultMessage, liarVictory)
            );

            //라이어 아닌사람 메시지 전송
            boolean isWinner = !liarVictory; // 라이어 승리 여부의 반대
            String userResultMessage = liarVictory ? liarWinMessage : liarLoseMessage;

            //System.out.println("[DEBUG] 사용자 " + member.name + "에게 전송할 메시지: " + userResultMessage);
            //System.out.println("[DEBUG] 승리 여부: " + isWinner);

            broadcastExceptUser(
                    liar,
                    new GameMsg(GameMsg.GAME_END, liar, userResultMessage, isWinner)
            );

            server.printDisplay("투표 결과 - 라이어 유추: " + liarCandidate);
            server.printDisplay(resultMessage);
            room.resetVoteCounts(); // 투표 초기화
            room.resetVoteCounts(); // 라이어 초기화
            server.printDisplay("[ServerManager] 게임 상태 초기화 완료");
        }

        private void broadcasting(GameMsg msg) {
            if (currentRoom == null) {
                server.printDisplay("broadcasting 실패: 클라이언트가 방에 속해 있지 않습니다.");
                return;
            }
            // 같은 방에 있는 멤버들에게만 메시지를 전송
            synchronized (currentRoom) {
                for (User member : currentRoom.getMembers()) {
//                    System.out.println("Broadcast 대상: " + member.name);
                    ClientHandler handler = findHandlerByUser(member);
                    if (handler != null) { // 핸들어 있을때
                        handler.sendGameMsg(msg);
                    }
                }
            }
        }

        private ClientHandler findHandlerByUser(User user) {
            for (ClientHandler handler : users) {
                if (handler.userName.equals(user.name)) {
                    return handler;
                }
            }
            return null;
        }

        private void broadcastIndividualUser(User liar, GameMsg msg) {
            if (currentRoom == null) {
                server.printDisplay("broadcasting 실패: 클라이언트가 방에 속해 있지 않습니다.");
                return;
            }
            // 같은 방에 있는 멤버들에게만 메시지를 전송
            synchronized (currentRoom) {
                for (User member : currentRoom.getMembers()) {
                    System.out.println("Broadcast 대상: " + member.name);
                    ClientHandler handler = findHandlerByUser(member);
                    if (handler.userName.equals(liar.name)) { // 라이어만
                        handler.isLiar = true;
                        handler.sendGameMsg(msg);
                    }
                }
            }
        }

        private void broadcastExceptUser(User liar, GameMsg msg) {
            if (currentRoom == null) {
                server.printDisplay("broadcasting 실패: 클라이언트가 방에 속해 있지 않습니다.");
                return;
            }
            // 같은 방에 있는 멤버들에게만 메시지를 전송
            synchronized (currentRoom) {
                for (User member : currentRoom.getMembers()) {
                    System.out.println("broadcastExceptUser 대상: " + member.name);
                    ClientHandler handler = findHandlerByUser(member);
                    if (!handler.userName.equals(liar.name)) { // 라이어 빼고
                        handler.sendGameMsg(msg);
                    }
                }
            }
        }

        private Room findRoom(String roomName) {
            synchronized (rooms) {
                // 방 검색
                Room room = rooms.stream()
                        .filter(r -> r.getRoomName().equals(roomName)) // 이름이 같은 방 필터링
                        .findFirst() // 첫 번째 방 반환
                        .orElseGet(() -> { // 방이 없으면 새로 생성
                            Room newRoom = new Room(roomName);
                            rooms.add(newRoom); // 새 방을 목록에 추가
                            server.printDisplay("새 방 생성: " + roomName);
                            return newRoom; // 새로 만든 방 반환
                        });
                return room;
            }
        }


        private void enterRoom(String roomName) {
            synchronized (rooms) {
                // 방 검색
                Room room = rooms.stream()
                        .filter(r -> r.getRoomName().equals(roomName)) // 이름이 같은 방 필터링
                        .findFirst() // 첫 번째 방 반환
                        .orElseGet(() -> { // 방이 없으면 새로 생성
                            Room newRoom = new Room(roomName);
                            rooms.add(newRoom); // 새 방을 목록에 추가
                            server.printDisplay("새 방 생성: " + roomName);
                            return newRoom; // 새로 만든 방 반환
                        });
                // 현재 클라이언트가 방에 속해있다면 제거
                if (currentRoom != null) {
                    currentRoom.removeMember(user);
                }

                // 유저의 방 정보 업데이트
//                user.joinRoom(room);
//                user.setCurrentRoom(room);
                currentRoom = room; // 현재 클라이언트의 방 업데이트

                // 방 이름에 따라 키워드 설정
                switch (currentRoom.getRoomName()) {
                    case "food":
                        currentRoom.setKeyword("햄버거");
                        break;
                    case "place":
                        currentRoom.setKeyword("에펠탑");
                        break;
                    case "animal":
                        currentRoom.setKeyword("사자");
                        break;
                    case "character":
                        currentRoom.setKeyword("뽀로로");
                        break;
                    default:
                        currentRoom.setKeyword("마카롱");
                }

//                server.printDisplay(userName + "님이 방 [" + user.getCurrentRoom().getRoomName() + "]에 입장했습니다. 현재 : " + room.getMemberCount() + "명");
            }
        }

        private void exitRoom() {
            synchronized (rooms) {
                if (currentRoom != null) {
                    // 현재 방에서 유저 제거
//                    currentRoom.removeMember(user);
                    user.leaveRoom();

                    // 방의 멤버가 아무도 없다면 방 삭제
                    if (currentRoom.getMembers().isEmpty()) {
                        rooms.remove(currentRoom);
                        server.printDisplay("빈 방 삭제: " + currentRoom.getRoomName());
                    }

                    // 현재 방 정보 초기화
                    currentRoom = null;
                }
            }
        }


        private User selectLiar(Vector<User> readyUsers) {
            // 랜덤으로 라이어 선택
            Random random = new Random();
            int liarIndex = random.nextInt(readyUsers.size());
            User liarUser = readyUsers.get(liarIndex);

            return liarUser;
        }

        private void disconnectClient() {
            if (user != null) {
                user.leaveRoom(); // User 메서드 호출
            }

            users.remove(this); // 클라이언트 목록에서 제거
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (clientSocket != null) clientSocket.close();
            } catch (IOException e) {
                server.printDisplay("클라이언트 소켓 닫기 오류: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            receiveMessage(clientSocket);
        }

    }



}

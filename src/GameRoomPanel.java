import javax.sound.sampled.Line;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Vector;

public class GameRoomPanel extends JPanel {
    private ClientManager clientManager;
    private GameMsg gameMsg;
    private Vector<String> userNames = new Vector<>();// 방에 들어온 유저 이름 저장

    private GamePanel gamePanel;

    public GameRoomPanel(ClientManager clientManager, GameMsg gameMsg) {
        this.clientManager = clientManager;
        this.gameMsg = gameMsg;
        gamePanel = new GamePanel(clientManager);

        buildGUI();
    }

    public void updateUser(Vector<String> userNames) {
        System.out.println("들어온 유저 목록: " + userNames);

        this.userNames = userNames;
        System.out.println("현재 userNames : " + userNames);

        refreshUserSidePanel();  // 유저 목록 UI 갱신
    }


    // 유저 목록 User Side Panel UI 갱신
    private void refreshUserSidePanel() {
        System.out.println("refreshUserSidePanel");

        // 기존에 있던 다른 패널들은 그대로 두고 userSidePanel만 갱신하기
        JPanel userSidePanel = createUserSidePanel();
        JPanel centerPanel = gamePanel.createCenterPanel();
        JPanel rightPanel = createRightPanel();

        // 기존 패널을 모두 삭제하고 다시 추가하는 대신, 필요한 부분만 갱신
        removeAll();  // 기존 UI를 삭제

        // userSidePanel만 갱신
        JLabel title = new JLabel(gameMsg.user.name + "님의 " + gameMsg.message + "방", SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);
        add(userSidePanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        revalidate();  // 레이아웃 갱신
        repaint();  // 화면 갱신
    }


    private void buildGUI() {
        setBounds(50, 200, 800, 600);
        setLayout(new BorderLayout());

        JLabel title = new JLabel(gameMsg.user.name + "님의 " + gameMsg.message + "방", SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);
        add(createUserSidePanel(), BorderLayout.WEST);
        add(gamePanel.createCenterPanel(), BorderLayout.CENTER);
        add(createRightPanel(), BorderLayout.EAST);
    }

    private JPanel createUserSidePanel(){
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 1));  // 1열 4행으로 세로로 나열

        panel.setBackground(Color.CYAN);

        // 현재 유저 목록을 JLabel로 표시
        for (String userName : userNames) {
            panel.add(new JLabel(userName));  // 유저 이름을 JLabel로 추가
        }

        return panel;
    }


    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout()); // 위아래로 나눔

        // 위쪽 채팅 패널
        JPanel chatPanel = ChatPanel();
        panel.add(chatPanel, BorderLayout.NORTH);

        // 아래쪽 이모티콘 패널
        JPanel imgPanel = ImgPanel();
        panel.add(imgPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel ChatPanel(){
        JPanel chatPanel = new JPanel();
        JLabel user = new JLabel("채팅 패널");
        chatPanel.setBackground(Color.ORANGE);

        chatPanel.add(user);
        return chatPanel;
    }

    private JPanel ImgPanel(){
        JPanel imgPanel = new JPanel();
        JLabel user = new JLabel("이모티콘 패널");
        imgPanel.setBackground(Color.pink);

        imgPanel.add(user);
        return imgPanel;
    }

}

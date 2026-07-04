package amiss.presentation.ui;

import amiss.application.service.GameServices;
import amiss.application.service.StatsService;
import amiss.application.service.TimeService;
import amiss.domain.model.User;

/**
 * Placeholder screen for the Z-Mart stop. Navigable (shows time/cash and returns to the
 * board) but has no store actions yet — the full shop is future work. See ROADMAP backlog.
 */
public class ZMartGUI extends javax.swing.JFrame {

    User user;
    GameServices services;
    private TimeService dist;
    private StatsService stat;

    public ZMartGUI(User u, GameServices services) {
        initComponents();
        user = u;
        this.services = services;
        dist = services.time();
        stat = services.stats();

        lblTimer.setText(dist.getNewTime(0));
        lblMoney.setText(Integer.toString(stat.getCash()));
    }

    private void initComponents() {

        lblMoney = new javax.swing.JLabel();
        pnlDisplay = new javax.swing.JScrollPane();
        txaNotification = new javax.swing.JTextArea();
        lblCash = new javax.swing.JLabel();
        btnExit = new javax.swing.JButton();
        lblTimer = new javax.swing.JLabel();
        lblTime = new javax.swing.JLabel();
        lblBackground = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Z-Mart");
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        getContentPane().add(lblMoney, new org.netbeans.lib.awtextra.AbsoluteConstraints(82, 53, 72, 26));

        txaNotification.setEditable(false);
        txaNotification.setColumns(20);
        txaNotification.setRows(5);
        txaNotification.setLineWrap(true);
        txaNotification.setWrapStyleWord(true);
        txaNotification.setText("Welcome to Z-Mart.\n\n(Coming soon.)");
        pnlDisplay.setViewportView(txaNotification);

        getContentPane().add(pnlDisplay, new org.netbeans.lib.awtextra.AbsoluteConstraints(12, 92, 260, 187));

        lblCash.setText("Cash");
        getContentPane().add(lblCash, new org.netbeans.lib.awtextra.AbsoluteConstraints(12, 53, 59, 26));

        btnExit.setText("Exit");
        btnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExitActionPerformed(evt);
            }
        });
        getContentPane().add(btnExit, new org.netbeans.lib.awtextra.AbsoluteConstraints(20, 290, 110, 36));

        getContentPane().add(lblTimer, new org.netbeans.lib.awtextra.AbsoluteConstraints(82, 13, 72, 26));

        lblTime.setText("Time");
        getContentPane().add(lblTime, new org.netbeans.lib.awtextra.AbsoluteConstraints(12, 13, 59, 26));

        lblBackground.setIcon(amiss.presentation.assets.Assets.icon("screens/ZMart.png"));
        getContentPane().add(lblBackground, new org.netbeans.lib.awtextra.AbsoluteConstraints(1, -4, 290, 350));

        pack();
        setLocationRelativeTo(null);
    }

    private void btnExitActionPerformed(java.awt.event.ActionEvent evt) {
        new MainGameGUI(user, services).setVisible(true);
        this.dispose();
    }

    private javax.swing.JButton btnExit;
    private javax.swing.JLabel lblBackground;
    private javax.swing.JLabel lblCash;
    private javax.swing.JLabel lblMoney;
    private javax.swing.JLabel lblTime;
    private javax.swing.JLabel lblTimer;
    private javax.swing.JScrollPane pnlDisplay;
    private javax.swing.JTextArea txaNotification;
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss;

import java.awt.Color;

/**
 * The Main Game Screen
 * @author The Rourke
 */
public class MainGameGUI extends javax.swing.JFrame {

    /**
     * Creates new form MainGameGUI
     */
    static User user;
    static DB db;

    TwoDGrid tdg = new TwoDGrid();
    CalcDuration dist = new CalcDuration();
    OpenLocation loc = new OpenLocation();
    Food eat = new Food();
    Stats stat = new Stats();
    University uni = new University();

    javax.swing.JButton[][] btnArr;

    /**
     *
     * @param u user object
     * @param d db object
     */
    public MainGameGUI(User u, DB d) {
        initComponents();
        user = u;
        db = d;

        String userName = user.getUser();
        btnNewRound.setVisible(false);
        lblCurrRound.setText(dist.getRound());

        String time = dist.getNewTime(0); // time is 0 when user saved and exit
        if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 1) && time.equals("0:00")) {
            stat.setDebt(txaNotification, 80);
            stat.setRent(txaNotification, 1);
        } else if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 0) && time.equals("0:00")) {
            stat.setRent(txaNotification, 1);
        } else if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 1)) {
            txaNotification.setText(txaNotification.getText() +"\nRent Is Due This Round");
            stat.setRent(txaNotification, 1);
        } else if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 0)){
            txaNotification.setText(txaNotification.getText() +"\nThank You for Paying Your Rent");
        } 
        
        btnStartNewGame.setVisible(false); //checks if the time is deplected and new round should start
        int cashG = stat.getCash();
        int happyG = Integer.parseInt(stat.getHappiness());
        int workG = Integer.parseInt(stat.getWork());
        int eduG = uni.getEducation();
        if (cashG >= 1000 && happyG >= 200 && workG >= 200 && eduG == 8) { //checks the users goals
            btnStartNewGame.setVisible(true);
            txaNotification.setText("congratulations, You have Completed the Game!");
        } else {
            txaNotification.setText(txaNotification.getText() + "\nWelcome " + userName + "\nThe Aim of the Game is to complete the goals in the least amount of time possible\n\nYour Current Stats Are as Follows\nCash: \n" + cashG + "/1000\nHappiness: \n" + happyG + "/200\nWork Experience: \n" + workG + "/200\nAnd Education: \n" + eduG + "/8\nWeeks of Food Stored:\n" + eat.getFood(txaNotification));

        }
        
        
        
        if (time.equals("0:00")) {
            btnNewRound.setVisible(true);
            btnPanel.setVisible(false);
            txaNotification.setText("Round has Ended");
        } else {
            lblTimer.setText(time);
        }

        btnArr = new javax.swing.JButton[4][4];

        tdg.toString(); //creates the board for the user to play on

        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if ((r == 1 || r == 2) && (c == 1 || c == 2)) {

                } else {
                    btnArr[r][c] = new javax.swing.JButton();

                    btnPanel.add(btnArr[r][c], new org.netbeans.lib.awtextra.AbsoluteConstraints(95 * r, 85 * c, 95, 85));

                    btnArr[r][c].setVisible(true);
                    btnArr[r][c].setBackground(Color.blue);

                    btnArr[r][c].setActionCommand("" + r + c);

                    btnArr[r][c].addActionListener(
                            new java.awt.event.ActionListener() {

                                public void actionPerformed(java.awt.event.ActionEvent evt) {

                                    int oldRow = dist.getX();
                                    int oldCol = dist.getY();

                                    char ch = evt.getActionCommand().charAt(0);
                                    int row = Integer.parseInt("" + ch);
                                    int col = Integer.parseInt("" + evt.getActionCommand().charAt(1));

                                    int multi = dist.getMulti(row, col); //gets the distance between two locations
                                    String time = dist.getNewTime(multi);

                                    switch (time) {
                                        case "Not Enough Time":
                                            txaNotification.setText(txaNotification.getText() + "\nNot Enough Time"); //message guide to user
                                            break;
                                        case "0:0":
                                            txaNotification.setText(txaNotification.getText() + "\nRound has Ended"); //message guide to user
                                            lblTimer.setText(time + "0");
                                            btnNewRound.setVisible(true);

                                            btnArr[oldRow][oldCol].setBackground(Color.BLUE);
                                            btnArr[0][0].setBackground(Color.YELLOW);

                                            btnPanel.setVisible(false);
                                            break;
                                        default:
                                            txaNotification.setText(txaNotification.getText() + "\nMoved " + multi + " blocks.");
                                            lblTimer.setText(time);
                                            dist.setPos(row, col);
                                            btnArr[row][col].setBackground(Color.YELLOW);
                                            btnArr[oldRow][oldCol].setBackground(Color.BLUE);

                                            MainGameGUI.this.dispose(); //closes the screen
                                            loc.openLocation(dist.toString(), user, db); //opens location when the user clicks on it
                                            break;
                                    }
                                    //}
                                }
                            }
                    );
                }
            }
        }
        btnArr[dist.getX()][dist.getY()].setBackground(Color.YELLOW);

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblTimer = new javax.swing.JLabel();
        btnHelp = new javax.swing.JButton();
        btnPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        txaNotification = new javax.swing.JTextArea();
        lblBoard = new javax.swing.JLabel();
        btnSAQ = new javax.swing.JButton();
        lblTime = new javax.swing.JLabel();
        lblRound = new javax.swing.JLabel();
        btnNewRound = new javax.swing.JButton();
        lblCurrRound = new javax.swing.JLabel();
        btnStartNewGame = new javax.swing.JButton();
        lblBackground = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Main Game");
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        lblTimer.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        getContentPane().add(lblTimer, new org.netbeans.lib.awtextra.AbsoluteConstraints(450, 120, 131, 29));

        btnHelp.setText("Help");
        btnHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHelpActionPerformed(evt);
            }
        });
        getContentPane().add(btnHelp, new org.netbeans.lib.awtextra.AbsoluteConstraints(430, 210, 219, -1));

        btnPanel.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        txaNotification.setEditable(false);
        txaNotification.setColumns(15);
        txaNotification.setLineWrap(true);
        txaNotification.setRows(20);
        txaNotification.setWrapStyleWord(true);
        txaNotification.setPreferredSize(new java.awt.Dimension(190, 170));
        jScrollPane2.setViewportView(txaNotification);

        btnPanel.add(jScrollPane2, new org.netbeans.lib.awtextra.AbsoluteConstraints(100, 90, 180, 160));

        lblBoard.setIcon(new javax.swing.ImageIcon("C:\\Users\\The Rourke\\Pictures\\Saved Pictures\\board.png")); // NOI18N
        btnPanel.add(lblBoard, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 380, 340));

        getContentPane().add(btnPanel, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 20, -1, -1));

        btnSAQ.setText("Save And Quit");
        btnSAQ.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSAQActionPerformed(evt);
            }
        });
        getContentPane().add(btnSAQ, new org.netbeans.lib.awtextra.AbsoluteConstraints(430, 170, 219, -1));

        lblTime.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        lblTime.setText("Time:");
        getContentPane().add(lblTime, new org.netbeans.lib.awtextra.AbsoluteConstraints(450, 90, 50, 20));

        lblRound.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        lblRound.setText("Round");
        getContentPane().add(lblRound, new org.netbeans.lib.awtextra.AbsoluteConstraints(550, 90, 70, 20));

        btnNewRound.setText("Start New Round");
        btnNewRound.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNewRoundActionPerformed(evt);
            }
        });
        getContentPane().add(btnNewRound, new org.netbeans.lib.awtextra.AbsoluteConstraints(470, 270, -1, -1));

        lblCurrRound.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        getContentPane().add(lblCurrRound, new org.netbeans.lib.awtextra.AbsoluteConstraints(550, 120, 70, 30));

        btnStartNewGame.setText("Start New Game");
        btnStartNewGame.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStartNewGameActionPerformed(evt);
            }
        });
        getContentPane().add(btnStartNewGame, new org.netbeans.lib.awtextra.AbsoluteConstraints(470, 320, 130, -1));

        lblBackground.setIcon(new javax.swing.ImageIcon("C:\\Users\\The Rourke\\Pictures\\PAT Backgrounds\\pale-blue2.jpg")); // NOI18N
        lblBackground.setText("Show HighScores");
        lblBackground.setToolTipText("");
        getContentPane().add(lblBackground, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 660, 380));

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void btnNewRoundActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewRoundActionPerformed
        btnNewRound.setVisible(false);
        btnPanel.setVisible(true);
        
        boolean eaten = eat.getEat(txaNotification);
        if (eaten == false) { //checks if the user ate last round
            dist.setTime(600);
            lblTimer.setText("10:00");
        } else {
            dist.setTime(720);
            lblTimer.setText("12:00");
        }
        btnArr[dist.getX()][dist.getY()].setBackground(Color.BLUE); //updates the new location on the board
        dist.setPos(0, 0);
        dist.setRound();
        
        String time = dist.getNewTime(0); // time is 0 when user saved and exit
        if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 1) && time.equals("0:00")) {
            stat.setDebt(txaNotification, 80);
            stat.setRent(txaNotification, 1);
        } else if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 0) && time.equals("0:00")) {
            stat.setRent(txaNotification, 1);
        } else if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 1)) {
            txaNotification.setText(txaNotification.getText() +"\nRent Is Due This Round");
            stat.setRent(txaNotification, 1);
        } else if (Integer.parseInt(dist.getRound()) % 4 == 0 && (stat.getRent() == 0)){
            txaNotification.setText(txaNotification.getText() +"\nThank You for Paying Your Rent");
        } 
        
        btnArr[0][0].setBackground(Color.YELLOW);

        lblCurrRound.setText(dist.getRound());
        
        int cashG = stat.getCash();
        int happyG = Integer.parseInt(stat.getHappiness());
        int workG = Integer.parseInt(stat.getWork());
        int eduG = uni.getEducation();
        
        if (cashG >= 1000 && happyG >= 200 && workG >= 200 && eduG == 8) { //checks the users goals
            btnStartNewGame.setVisible(true);
            txaNotification.setText("congratulations, You have Completed the Game!"); //message guide to user
        } else {
            txaNotification.setText("Your Current Stats Are as Follows\nCash: \n" + cashG + "/1000\nHappiness: \n" + happyG + "/200\nWork Experience: \n" + workG + "/200\nAnd Education: \n" + eduG + "/8\n\nWeeks of Food Stored:\n" + eat.getFood(txaNotification));

        }
    }//GEN-LAST:event_btnNewRoundActionPerformed

    private void btnSAQActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSAQActionPerformed
        this.dispose(); //closes the screen
    }//GEN-LAST:event_btnSAQActionPerformed

    private void btnHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHelpActionPerformed
        new HelpGUI().setVisible(true);
    }//GEN-LAST:event_btnHelpActionPerformed

    private void btnStartNewGameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStartNewGameActionPerformed
        stat.reset(txaNotification);
        new MainGameGUI(user, db).setVisible(true);
        this.dispose(); //closes the screen and restarts
    }//GEN-LAST:event_btnStartNewGameActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;

                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(MainGameGUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(MainGameGUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(MainGameGUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(MainGameGUI.class
                    .getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainGameGUI(user, db).setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnHelp;
    private javax.swing.JButton btnNewRound;
    private javax.swing.JPanel btnPanel;
    private javax.swing.JButton btnSAQ;
    private javax.swing.JButton btnStartNewGame;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lblBackground;
    private javax.swing.JLabel lblBoard;
    private javax.swing.JLabel lblCurrRound;
    private javax.swing.JLabel lblRound;
    private javax.swing.JLabel lblTime;
    private javax.swing.JLabel lblTimer;
    private javax.swing.JTextArea txaNotification;
    // End of variables declaration//GEN-END:variables
}

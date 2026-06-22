/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss;

/**
 * Class that opens up each screen depending on the user coordinates
 * @author The Rourke
 */
public class OpenLocation {

    /**
     * Opens up each screen depending on the user coordinates
     * @param pos Users Coordinates
     * @param usr User Object
     * @param d DB Object
     */
    public void openLocation(String pos, User usr, DB d) {

        User user = usr;
        DB db = d;

        String location = pos;

        switch (location) {
            case "0:0":
                new LowClassResidenceGUI(user, db).setVisible(true);
                break;
            case "1:0":
                new PublicPoolGUI(user, db).setVisible(true);
                break;
            case "2:0":
                new FastFoodGUI(user, db).setVisible(true);
                break;
            case "3:0":
                new UniversityGUI(user, db).setVisible(true);
                break;
            case "0:1":
                new RentOfficeGUI(user, db).setVisible(true);
                break;
            case "3:1":
                new EmploymentGUI(user, db).setVisible(true);
                break;
            case "0:2":
                new BankGUI(user, db).setVisible(true);
                break;
            case "3:2":
                new ApplianceStoreGUI(user, db).setVisible(true);
                break;
            case "0:3":
                new PizzaPalaceGUI(user, db).setVisible(true);
                break;
            case "1:3":
                new MarketGUI(user, db).setVisible(true);
                break;
            case "2:3":
                new ClothesStoreGUI(user, db).setVisible(true);
                break;
            case "3:3":
                new FactoryGUI(user, db).setVisible(true);
                break;
            default:
                break;
        }
    }
}

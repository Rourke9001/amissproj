/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package amiss.presentation.ui;
import amiss.domain.model.User;
import amiss.application.service.GameServices;

/**
 * Class that opens up each screen depending on the user coordinates
 * @author The Rourke
 */
public class OpenLocation {

    /**
     * Opens up each screen depending on the user coordinates
     * @param pos Users Coordinates
     * @param usr User Object
     * @param services the wired game services for the current player
     */
    public void openLocation(String pos, User usr, GameServices services) {

        User user = usr;

        String location = pos;

        switch (location) {
            case "0:0":
                new LowClassResidenceGUI(user, services).setVisible(true);
                break;
            case "1:0":
                new PublicPoolGUI(user, services).setVisible(true);
                break;
            case "2:0":
                new FastFoodGUI(user, services).setVisible(true);
                break;
            case "3:0":
                new UniversityGUI(user, services).setVisible(true);
                break;
            case "0:1":
                new RentOfficeGUI(user, services).setVisible(true);
                break;
            case "3:1":
                new EmploymentGUI(user, services).setVisible(true);
                break;
            case "0:2":
                new BankGUI(user, services).setVisible(true);
                break;
            case "3:2":
                new ApplianceStoreGUI(user, services).setVisible(true);
                break;
            case "0:3":
                new PizzaPalaceGUI(user, services).setVisible(true);
                break;
            case "1:3":
                new MarketGUI(user, services).setVisible(true);
                break;
            case "2:3":
                new ClothesStoreGUI(user, services).setVisible(true);
                break;
            case "3:3":
                new FactoryGUI(user, services).setVisible(true);
                break;
            default:
                break;
        }
    }
}

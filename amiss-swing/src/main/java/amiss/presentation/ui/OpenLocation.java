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

        // Cells map to the 13-stop clockwise loop (see amiss.domain.board.Board).
        switch (location) {
            case "0:0": // Low-Cost Housing (start)
                new LowClassResidenceGUI(user, services).setVisible(true);
                break;
            case "0:1": // Pawn Shop
                new PawnShopGUI(user, services).setVisible(true);
                break;
            case "0:2": // Z-Mart
                new ZMartGUI(user, services).setVisible(true);
                break;
            case "0:3": // Monolith Burgers
                new FastFoodGUI(user, services).setVisible(true);
                break;
            case "0:4": // QT Clothing
                new ClothesStoreGUI(user, services).setVisible(true);
                break;
            case "1:4": // Socket City
                new ApplianceStoreGUI(user, services).setVisible(true);
                break;
            case "2:4": // Hi-Tech U
                new UniversityGUI(user, services).setVisible(true);
                break;
            case "3:4": // Employment Office
                new EmploymentGUI(user, services).setVisible(true);
                break;
            case "3:3": // Factory
                new FactoryGUI(user, services).setVisible(true);
                break;
            case "3:1": // Bank
                new BankGUI(user, services).setVisible(true);
                break;
            case "3:0": // Black's Market
                new MarketGUI(user, services).setVisible(true);
                break;
            case "2:0": // Le Security Apartments
                new LeSecurityApartmentsGUI(user, services).setVisible(true);
                break;
            case "1:0": // Rent Office
                new RentOfficeGUI(user, services).setVisible(true);
                break;
            default:
                break;
        }
    }
}

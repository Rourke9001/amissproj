package amiss;

import java.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to connect to the database
 * @author The Rourke
 */
public class DB {

    private static final Logger log = LoggerFactory.getLogger(DB.class);
    private static final String driver = "com.mysql.cj.jdbc.Driver";
    private static final String user = "root";
    private static final String password = "password";
    private static final String url =
            "jdbc:mysql://localhost:3306/amissdb"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;

    /**
     * Object which connects the SQL database
     */
    public DB() {
        try {
            Class.forName(driver);
            connection = DriverManager.getConnection(url, user, password);
            log.info("Connection Successful");
        } catch (SQLException s) {
            log.error("Cannot connect to database: {}", s.getMessage());
        } catch (ClassNotFoundException c) {
            log.error("Cannot load driver", c);
        }
    }

    /**
     * Generates a result set from the table
     * @param qry the query to the database
     * @return returns a result set from the table
     * @throws SQLException
     */
    public ResultSet query(String qry) throws SQLException {
        statement = connection.prepareStatement(qry);
        resultSet = statement.executeQuery();
        return resultSet;
    }

    /**
     * Updates the database
     * @param qry the query to the database
     * @throws SQLException
     */
    public void update(String qry) throws SQLException {
        statement = connection.prepareStatement(qry);
        statement.executeUpdate();
        statement.close();
    }
}

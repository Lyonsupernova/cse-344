package flightapp;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.Properties;

public abstract class QueryAbstract {
  // DB Connection
  protected Connection conn;

  // For check dangling
  private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
  private PreparedStatement tranCountStatement;

  protected QueryAbstract(Connection conn) throws SQLException {
    this.conn = conn;
    tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
  }

  /**
   * Get underlying connection
   */
  public Connection getConnection() {
    return conn;
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Return a connecion by using dbconn.properties file
   *
   * @throws SQLException
   * @throws IOException
   */
  public static Connection openConnectionFromDbConn() throws SQLException, IOException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("flightapp.server_url");
    String dbName = configProps.getProperty("flightapp.database_name");
    String adminName = configProps.getProperty("flightapp.username");
    String password = configProps.getProperty("flightapp.password");
    return openConnectionFromCredential(serverURL, dbName, adminName, password);
  }

  /**
   * Return a connecion by using the provided parameter.
   *
   * @param serverURL example: example.database.widows.net
   * @param dbName    database name
   * @param adminName username to login server
   * @param password  password to login server
   *
   * @throws SQLException
   */
  protected static Connection openConnectionFromCredential(String serverURL, String dbName,
      String adminName, String password) throws SQLException {
    String connectionUrl =
        String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
            dbName, adminName, password);
    Connection conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

    return conn;
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public final void clearTablesWrap() {
    try {
      clearTables();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract void clearTables() throws SQLException;

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public final String login(String username, String password) {
    try {
      return transaction_login(username, password);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_login(String username, String password);

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public final String createCustomer(String username, String password, int initAmount) {
    try {
      return transaction_createCustomer(username, password, initAmount);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_createCustomer(String username, String password, int initAmount);

  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
   * is searches for direct flights and flights with two "hops." Only searches for up to the number
   * of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Query.Flight#toString()
   */
  public final String search(String originCity, String destinationCity, boolean directFlight,
                                   int dayOfMonth, int numberOfItineraries) {
    try {
      return transaction_search(originCity, destinationCity, directFlight, dayOfMonth, numberOfItineraries);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_search(String originCity, String destinationCity, boolean directFlight,
                                            int dayOfMonth, int numberOfItineraries);

  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *                    the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   *         If the user is trying to book an itinerary with an invalid ID or without having done a
   *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
   *         a reservation on the same day as the one that they are trying to book now, then return
   *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
   *         failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from 1 and
   *         increments by 1 each time a successful reservation is made by any user in the system.
   */
  public final String book(int itineraryId) {
    try {
      return transaction_book(itineraryId);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_book(int itineraryId);

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   *         is not found / not under the logged in user's name, then return "Cannot find unpaid
   *         reservation [reservationId] under user: [username]\n" If the user does not have enough
   *         money in their account, then return "User has only [balance] in account but itinerary
   *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
   *         [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public final String pay(int reservationId) {
    try {
      return transaction_pay(reservationId);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_pay(int reservationId);

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Query.Flight#toString()
   */
  public final String reservations() {
    try {
      return transaction_reservations();
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_reservations();

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *         all other errors, return "Failed to cancel reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public final String cancel(int reservationId) {
    try {
      return transaction_cancel(reservationId);
    } finally {
      checkDanglingTransaction();
    }
  }

  public abstract String transaction_cancel(int reservationId);

  /**
   * Throw IllegalStateException if transaction not completely complete, rollback.
   *
   */
  private void checkDanglingTransaction() {
    try {
      try (ResultSet rs = tranCountStatement.executeQuery()) {
        rs.next();
        int count = rs.getInt("tran_count");
        if (count > 0) {
          throw new IllegalStateException(
              "\nTransaction not fully commit/rollback. Number of transaction in process: " + count
              + "\nImportant: transaction have to either commit or rollback before any return. Example: " +
                  "flight is fulled, conn.rollback() before return the error string.\n");
        }
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database error", e);
    }
  }
}

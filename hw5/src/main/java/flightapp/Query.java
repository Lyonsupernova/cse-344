package flightapp;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.transform.Result;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.*;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // capacity table
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Capacity WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  private static final String UPDATE_FLIGHT_CAPACITY = "UPDATE Capacity SET capacity = ? WHERE fid = ?";
  private PreparedStatement updateFlightCapacityStatement;

  // clear the table
  private static final String CLEAR_USER_TABLE = "DELETE FROM Users";
  private PreparedStatement clearUserTableStatement;

  private static final String CLEAR_ITINERARIES_TABLE = "DELETE FROM Itineraries";
  private PreparedStatement clearItineraryStatement;

  private static final String CLEAR_RESERVATION_TABLE = "DELETE FROM Reservations";
  private PreparedStatement clearReservationStatement;

  // select username = ?
  private static final String SELECT_USERNAME = "SELECT * From Users WHERE UserName = ?";
  private PreparedStatement selectUserNameStatement;

  // update user balance
  private static final String UPDATE_BALANCE = "UPDATE Users SET Balance = ? WHERE UserName = ?";
  private PreparedStatement updateBalanceStatement;

  // update reservation table
  private static final String UPDATE_RESERVATION = "UPDATE Reservations SET IsPaid = 1 WHERE ReservationID = ?";
  private PreparedStatement updateReservationStatement;


  // create new user
  private static final String INSERT_USER = "INSERT INTO Users (UserName,Password,Balance,Salt) VALUES (?,?,?,?)";
  private PreparedStatement insertUserStatement;

  // insert into itinerary rows
  private static final String INSERT_ITINERARY = "INSERT INTO Itineraries (ItineraryID,fid1,fid2) VALUES (?,?,?)";
  private PreparedStatement insertItineraryStatement;

  // direct flight search select statement
  private static final String DIRECT_FLIGHT_SEARCH =
          "SELECT TOP (?) fid,day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price"
                  + " FROM Flights"
                  + " WHERE origin_city = ? AND dest_city = ? AND canceled = 0"
                  + " AND day_of_month = ?"
                  + " ORDER BY actual_time ASC;";
  private PreparedStatement directStatement;

  // indirect flight search select statement
  private static final String INDIRECT_FLIGHT_SEARCH = "SELECT TOP (?) " +
          "f1.fid AS f1_fid, f1.day_of_month AS f1_day_of_month, f1.carrier_id AS f1_carrier_id, " +
          "f1.flight_num AS f1_flight_num, f1.origin_city AS f1_origin_city, " +
          "f1.dest_city AS f1_dest_city, f1.actual_time AS f1_actual_time, f1.capacity AS f1_capacity, " +
          "f1.price AS f1_price," +
          "f2.fid AS f2_fid, f2.day_of_month AS f2_day_of_month, f2.carrier_id AS f2_carrier_id," +
          "f2.flight_num AS f2_flight_num, f2.origin_city AS f2_origin_city," +
          "f2.dest_city AS f2_dest_city, f2.actual_time AS f2_actual_time, f2.capacity AS f2_capacity, " +
          "f2.price AS f2_price " +
          "FROM Flights AS f1, Flights AS f2 " +
          "WHERE f1.origin_city = ? AND " +
          "f2.dest_city = ? AND " +
          "f1.day_of_month = ? AND " +
          "f1.dest_city = f2.origin_city AND " +
          "f1.canceled = 0 AND " +
          "f2.day_of_month = ? AND " +
          "f2.canceled = 0" +
          "ORDER BY f1.actual_time + f2.actual_time, f1.fid, f2.fid ASC";
  private PreparedStatement indirectStatement;

  // search itinerary with itinerary ID
  private static final String SEARCH_ITINERARY = "SELECT * FROM Itineraries WHERE ItineraryID = ?";
  private PreparedStatement searchItineraryStatement;

  // search flight with FID
  private static final String SEARCH_FID = "SELECT * FROM FLIGHTS WHERE fid = ?";
  private PreparedStatement searchFIDStatement;

  // search current date with reservation
  private static final String SEARCH_RESERVATION = "SELECT F.day_of_month AS flight_day_of_month" +
          " FROM Reservations AS R, " +
          "Itineraries AS I, FLIGHTS AS F WHERE R.ItineraryID = I.ItineraryID AND I.fid1 = F.fid";
  private PreparedStatement searchReservationStatement;

  // the current reservation id
  private static final String SEARCH_RESERVE_COUNT =  "SELECT COUNT(*) AS count FROM Reservations WHERE UserName = ?";
  private PreparedStatement reservationCountStatement;

  // insert reservation id
  private static final String INSERT_RESERVATION = "INSERT INTO Reservations VALUES (?,?,?,?,?)";
  private PreparedStatement insertReservationStatement;

  private static final String SELECT_RESERVATION = "SELECT * FROM Reservations WHERE ReservationID = ?" +
          " AND IsCancelled = 0";
  private PreparedStatement selectReservationStatement;

  private static final String SELECT_RESERVATION_WITH_USER_NAME = "SELECT * FROM Reservations WHERE UserName = ? " +
          "AND IsCancelled = 0";
  private PreparedStatement selectReservationWithUserNameStatement;

  private static final String UPDATE_RESERVATION_CANCEL = "UPDATE Reservations SET IsCancelled = 1 " +
          "WHERE reservationID = ?";
  private PreparedStatement updateCancelReservationStatement;

  // user logged in status
  private static boolean isLogin;
  private static String loginUserName;
  public Query(Connection conn) throws SQLException {
    super(conn);
    prepareStatements();
    isLogin = false;
    clearItineraryStatement.executeUpdate();
    loginUserName = null;
  }

  public Query() throws SQLException, IOException {
    this(openConnectionFromDbConn());
  }

  protected Query(String serverURL, String dbName, String adminName, String password)
      throws SQLException {
    this(openConnectionFromCredential(serverURL, dbName, adminName, password));
  }

  /**
   * Clear the data in any custom tables created.
   * <p>
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() throws SQLException{
    // Note: since failing here break stuff, you don't want
    // to catch exception but instead throw it so your program will
    // be broken right away, easier to debug.
    clearUserTableStatement.executeUpdate();
    clearItineraryStatement.executeUpdate();
    clearReservationStatement.executeUpdate();
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    updateFlightCapacityStatement = conn.prepareStatement(UPDATE_FLIGHT_CAPACITY);
    clearUserTableStatement = conn.prepareStatement(CLEAR_USER_TABLE);
    clearItineraryStatement = conn.prepareStatement(CLEAR_ITINERARIES_TABLE);
    clearReservationStatement = conn.prepareStatement(CLEAR_RESERVATION_TABLE);
    selectUserNameStatement = conn.prepareStatement(SELECT_USERNAME);
    insertUserStatement = conn.prepareStatement(INSERT_USER);
    directStatement = conn.prepareStatement(DIRECT_FLIGHT_SEARCH);
    indirectStatement = conn.prepareStatement(INDIRECT_FLIGHT_SEARCH);
    insertItineraryStatement = conn.prepareStatement(INSERT_ITINERARY);
    searchItineraryStatement = conn.prepareStatement(SEARCH_ITINERARY);
    searchFIDStatement = conn.prepareStatement(SEARCH_FID);
    searchReservationStatement = conn.prepareStatement(SEARCH_RESERVATION);
    reservationCountStatement = conn.prepareStatement(SEARCH_RESERVE_COUNT);
    insertReservationStatement = conn.prepareStatement(INSERT_RESERVATION);
    selectReservationStatement = conn.prepareStatement(SELECT_RESERVATION);
    updateBalanceStatement = conn.prepareStatement(UPDATE_BALANCE);
    updateReservationStatement = conn.prepareStatement(UPDATE_RESERVATION);
    selectReservationWithUserNameStatement = conn.prepareStatement(SELECT_RESERVATION_WITH_USER_NAME);
    updateCancelReservationStatement = conn.prepareStatement(UPDATE_RESERVATION_CANCEL);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   * errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    try {
      // if the user logged in, loginMap == true
      clearItineraryStatement.executeUpdate();
      String userNameToLowerCase = username.toLowerCase();
      if (isLogin) {
        return "User already logged in\n";
      }
      // authentication

      // select salt
      selectUserNameStatement.clearParameters();
      selectUserNameStatement.setString(1, userNameToLowerCase);
      ResultSet rs = selectUserNameStatement.executeQuery();
      if (!rs.next()) {
        return "Login failed\n";
      }
      byte[] salt = rs.getBytes("Salt");
      byte[] passHash = rs.getBytes("Password");
      byte[] hash = getPassHash(password, salt);
      if (!Arrays.equals(passHash, hash)) {
        return "Login failed\n";
      }
      // set the loginMap as true
      isLogin = true;
      loginUserName = userNameToLowerCase;
      rs.close();
    } catch (SQLException throwables) {
      throwables.printStackTrace();
    }
    return "Logged in as " + username + "\n";
  }

  private byte[] getPassHash(String password, byte[] salt) {
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH,
            KEY_LENGTH);
    // Generate the hash
    SecretKeyFactory factory = null;
    byte[] hash = null;
    try {
      factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      hash = factory.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      throw new IllegalStateException();
    }
    return hash;
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      if (initAmount < 0 || username.length() > 20 || password.length() > 20) {
        return "Failed to create user\n";
      }
      // if the user exists
      String userNameToLowerCase = username.toLowerCase();
      selectUserNameStatement.clearParameters();
      selectUserNameStatement.setString(1, userNameToLowerCase);
      ResultSet rs = selectUserNameStatement.executeQuery();
      if (rs.next()) {
        return "Failed to create user\n";
      }
      rs.close();
      // salt passhash
      SecureRandom random = new SecureRandom();
      byte[] salt = new byte[16];
      random.nextBytes(salt);
      // Specify the hash parameters
      byte[] hash = getPassHash(password, salt);
      insertUser(userNameToLowerCase, hash, initAmount, salt);

    } catch (SQLException throwables) {
      throwables.printStackTrace();
    }
    return "Created user " + username + "\n";
  }


  private void insertUser(String userNameToLowerCase, byte[] hash, int initAmount, byte[] salt) throws SQLException {
      insertUserStatement.clearParameters();
      insertUserStatement.setString(1, userNameToLowerCase);
      insertUserStatement.setBytes(2, hash);
      insertUserStatement.setInt(3, initAmount);
      insertUserStatement.setBytes(4, salt);
      insertUserStatement.executeUpdate();
  }

  /**
   * Implement the search function.
   * <p>
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
   * is searches for direct flights and flights with two "hops." Only searches for up to the number
   * of itineraries given by {@code numberOfItineraries}.
   * <p>
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   * occurs, then return "Failed to search\n".
   * <p>
   * Otherwise, the sorted itineraries printed in the following format:
   * <p>
   * Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   * minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   * <p>
   * Each flight should be printed using the same format as in the {@code Flight} class.
   * Itinerary numbers in each search should always start from 0 and increase by 1.
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight,
                                   int dayOfMonth, int numberOfItineraries) {
    // WARNING the below code is unsafe and only handles searches for direct flights
    // You can use the below code as a starting reference point or you can get rid
    // of it all and replace it with your own implementation.
    //
    // It will not pass test because it try to create a statement, but you
    // can still run FlightService
    //
    try {
      // WARNING the below code is unsafe and only handles searches for direct flights
      // You can use the below code as a starting reference point or you can get rid
      // of it all and replace it with your own implementation.
      //
      clearItineraryStatement.executeUpdate();
      StringBuffer sb = new StringBuffer();
      List<Itinerary> flightArr = new ArrayList<>();
      directStatement.clearParameters();
      directStatement.setInt(1, numberOfItineraries);
      directStatement.setString(2, originCity);
      directStatement.setString(3, destinationCity);
      directStatement.setInt(4, dayOfMonth);
      ResultSet rs = directStatement.executeQuery();
      int directCount = 0;
      if (!rs.next()) {
        return "No flights match your selection\n";
      }
      do {
        Flight flight = new Flight();
        setFlight(flight, rs, "");
        flightArr.add(new Itinerary(flight, null));
        directCount++;
      } while (rs.next());
      if (!directFlight && directCount < numberOfItineraries) {
        // execute indirect flight search
        indirectStatement.setInt(1, numberOfItineraries - directCount);
        indirectStatement.setString(2, originCity);
        indirectStatement.setString(3, destinationCity);
        indirectStatement.setInt(4, dayOfMonth);
        indirectStatement.setInt(5, dayOfMonth);
        ResultSet rss = indirectStatement.executeQuery();
        while (rss.next()) {
          Flight flight1 = new Flight();
          setFlight(flight1, rss, "f1_");
          Flight flight2 = new Flight();
          setFlight(flight2, rss, "f2_");
          flightArr.add(new Itinerary(flight1, flight2));
        }
        rss.close();
      }
      // sort
      Collections.sort(flightArr, (o1, o2) -> {
        if (o1.getTotalTime() != o2.getTotalTime()) {
          return o1.getTotalTime() - o2.getTotalTime();
        } else if (o1.flight1.fid != o2.flight1.fid) {
          return o1.flight1.fid - o2.flight1.fid;
        }
        return o1.flight2.fid - o2.flight2.fid;
      });
      // update itinerary table
      for (int i = 0; i < flightArr.size(); i++) {
        if (flightArr.get(i).flight1 != null && flightArr.get(i).flight2 == null) {
          // string buffer print out str
          // Itinerary 0: 2 flight(s), 317 minutes
          sb.append("Itinerary " + i + ": 1 flight(s), " + flightArr.get(i).getTotalTime() + " minutes\n");
          sb.append(flightArr.get(i).flight1.toString() + "\n");
          // itinerary table insert rows
          // i, flightArr[i].flight1.fid, null
          insertItineraryStatement.setInt(1, i);
          insertItineraryStatement.setInt(2, flightArr.get(i).flight1.fid);
          insertItineraryStatement.setNull(3, Types.INTEGER);
          insertItineraryStatement.executeUpdate();
        } else if (flightArr.get(i).flight1 != null && flightArr.get(i).flight2 != null) {
          sb.append("Itinerary " + i + ": 2 flight(s), " + flightArr.get(i).getTotalTime() + " minutes\n");
          sb.append(flightArr.get(i).flight1.toString() + "\n");
          sb.append(flightArr.get(i).flight2.toString() + "\n");
          insertItineraryStatement.setInt(1, i);
          insertItineraryStatement.setInt(2, flightArr.get(i).flight1.fid);
          insertItineraryStatement.setInt(3, flightArr.get(i).flight2.fid);
          insertItineraryStatement.executeUpdate();
        }
      }
      rs.close();
      return sb.toString();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return "Failed to search\n";
  }


  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *                    the current session.
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   * If the user is trying to book an itinerary with an invalid ID or without having done a
   * search, then return "No such itinerary {@code itineraryId}\n". If the user already has
   * a reservation on the same day as the one that they are trying to book now, then return
   * "You cannot book two flights in the same day\n". For all other errors, return "Booking
   * failed\n".
   * <p>
   * And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   * where reservationId is a unique number in the reservation system that starts from 1 and
   * increments by 1 each time a successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    if (!isLogin) {
      return "Cannot book reservations, not logged in\n";
    }
    try {
      searchItineraryStatement.clearParameters();
      searchItineraryStatement.setInt(1, itineraryId);
      ResultSet rs = searchItineraryStatement.executeQuery();
      if (!rs.next()) {
        return "No such itinerary " + itineraryId + "\n";
      }
      int fid1 = rs.getInt("fid1");
      int fid2 = rs.getInt("fid2");
      // check capacity
      // check the first flight
      int capacity = checkFlightCapacity(fid1);
      if (capacity == 0) {
        return "Booking failed\n";
      }
      updateCapacity(capacity, fid1);
      if (fid2 != 0) {
        capacity = checkFlightCapacity(fid2);
        if (capacity == 0) {
          return "Booking failed\n";
        }
        updateCapacity(capacity, fid2);
      }

      // search itinerary day_of_month
      searchFIDStatement.setInt(1, fid1);
      rs = searchFIDStatement.executeQuery();
      rs.next();
      int date = rs.getInt("day_of_month");
      rs = searchReservationStatement.executeQuery();
      while (rs.next()) {
        if (rs.getInt("flight_day_of_month") == date) {
          return "You cannot book two flights in the same day\n";
        }
      }
      // insert into the reservation tables
      // get reservation id
      int reservationID = getReservationID();
      insertReservation(reservationID, itineraryId, loginUserName);
      rs.close();
      return "Booked flight(s), reservation ID: " + getReservationID() + "\n";
    } catch (SQLException throwables) {
      throwables.printStackTrace();
      return "Booking failed\n";
    }
  }

  private void insertReservation(int reservationID, int itineraryId, String loginUserName) throws SQLException {
    insertReservationStatement.clearParameters();
    insertReservationStatement.setInt(1, reservationID);
    insertReservationStatement.setInt(2, 0);
    insertReservationStatement.setInt(3, 0);
    insertReservationStatement.setInt(4, itineraryId);
    insertReservationStatement.setString(5, loginUserName);
    insertReservationStatement.executeQuery();
  }

  private int getReservationID() throws SQLException {
    reservationCountStatement.clearParameters();
    reservationCountStatement.setString(1, loginUserName);
    ResultSet rs = reservationCountStatement.executeQuery();
    rs.next();
    int count = rs.getInt("count");
    rs.close();
    return count + 1;
  }
  private void updateCapacity(int capacity, int fid) throws SQLException {
    // update fid1 capacity 1. capacity 2. fid
    updateFlightCapacityStatement.clearParameters();
    updateFlightCapacityStatement.setInt(1, capacity - 1);
    updateFlightCapacityStatement.setInt(2, fid);
    updateFlightCapacityStatement.executeUpdate();
  }

  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   * is not found / not under the logged in user's name, then return "Cannot find unpaid
   * reservation [reservationId] under user: [username]\n" If the user does not have enough
   * money in their account, then return "User has only [balance] in account but itinerary
   * costs [cost]\n" For all other errors, return "Failed to pay for reservation
   * [reservationId]\n"
   * <p>
   * If successful, return "Paid reservation: [reservationId] remaining balance:
   * [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    if (!isLogin) {
      return "Cannot pay, not logged in\n";
    }
    // select the reservation with reservation ID
    try {
      // "SELECT * FROM Reservations WHERE ReservationID = ?";
      selectReservationStatement.clearParameters();
      selectReservationStatement.setInt(1, reservationId);
      ResultSet rs = selectReservationStatement.executeQuery();
      if (!rs.next()) {
        return "Cannot find unpaid reservation " + reservationId + "under user: " + loginUserName + "\n";
      }
      int isPaid = rs.getInt("IsPaid");
      if (isPaid == 1) {
        return "Failed to pay for reservation " + reservationId + "\n";
      }
      // select the user balance
      int balance = getUserBalance();
      int price = getTotalTicketPrice(rs);
      if (price > balance) {
        return "User has only " + balance +" in account but itinerary costs " + price + "\n";
      }
      // update the balance
      // update the reservation table set isPaid = 1
      updateBalanceStatement.clearParameters();
      // "UPDATE Users SET Balance = ? WHERE UserName = ?";
      int remain = balance - price;
      updateBalance(remain);
      updateUnpaidReservationToPaid(reservationId);
      // "UPDATE Reservations SET IsPaid = 1 WHERE ReservationID = ?";
      rs.close();
      // reservation table get itinerary ID
      // select the flight's price
      // flight1 + flight2
      return "Paid reservation: " + reservationId + " remaining balance: " + remain + "\n";
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return "Failed to pay for reservation " + reservationId + "\n";
  }

  private int getTotalTicketPrice(ResultSet rs) throws SQLException {
    int itineraryID = rs.getInt("ItineraryID");
    searchItineraryStatement.clearParameters();
    searchItineraryStatement.setInt(1, itineraryID);
    ResultSet rss = searchItineraryStatement.executeQuery();
    rss.next();
    int fid1 = rss.getInt("fid1");
    int fid2 = rss.getInt("fid2");
    searchFIDStatement.clearParameters();
    searchFIDStatement.setInt(1, fid1);
    rss = searchFIDStatement.executeQuery();
    rss.next();
    int price = rss.getInt("price");
    if (fid2 != 0) {
      searchFIDStatement.clearParameters();
      searchFIDStatement.setInt(2, fid2);
      rss = searchItineraryStatement.executeQuery();
      rss.next();
      price += rss.getInt("price");
    }
    rss.close();
    return price;
  }
  private void updateUnpaidReservationToPaid(int reservationId) throws SQLException {
    updateReservationStatement.clearParameters();
    updateReservationStatement.setInt(1, reservationId);
    updateReservationStatement.executeQuery();
  }

  private void updateBalance(int remain) throws SQLException {
    updateBalanceStatement.clearParameters();
    updateBalanceStatement.setInt(1, remain);
    updateBalanceStatement.setString(2, loginUserName);
    updateBalanceStatement.executeUpdate();
  }

  private int getUserBalance() throws SQLException {
    selectUserNameStatement.clearParameters();
    selectUserNameStatement.setString(1, loginUserName);
    ResultSet rs = selectUserNameStatement.executeQuery();
    rs.next();
    return rs.getInt("balance");
  }
  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   * the user has no reservations, then return "No reservations found\n" For all other
   * errors, return "Failed to retrieve reservations\n"
   * <p>
   * Otherwise return the reservations in the following format:
   * <p>
   * Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   * reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   * [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   * reservation]\n ...
   * <p>
   * Each flight should be printed using the same format as in the {@code Flight} class.
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    if (!isLogin) {
      return "Cannot view reservations, not logged in\n";
    }
    // select all the reservations from the users
    try {
      selectReservationWithUserNameStatement.clearParameters();
      selectReservationWithUserNameStatement.setString(1, loginUserName);
      ResultSet rs = selectReservationWithUserNameStatement.executeQuery();
      if (!rs.next()) {
        return "No reservations found\n";
      }
      StringBuffer sb = new StringBuffer();
      do {
        int reservationID = rs.getInt("ReservationID");
        boolean isPaid = rs.getInt("IsPaid") == 1? true : false;
        // select flight information
        sb.append("Reservation " + reservationID + " paid: " + isPaid + "\n");
        int itineraryID = rs.getInt("ItineraryID");
        searchItineraryStatement.clearParameters();
        searchItineraryStatement.setInt(1, itineraryID);
        rs = searchItineraryStatement.executeQuery();
        rs.next();
        int fid1 = rs.getInt("fid1");
        Flight flight1 = getFlight(fid1);
        sb.append(flight1 + "\n");
        int fid2 = rs.getInt("fid2");
        if (fid2 != 0) {
          Flight flight2 = getFlight(fid2);
          sb.append(flight2 + "\n");
        }
      } while (rs.next());
      return sb.toString();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return "Failed to retrieve reservations\n";
  }

  private Flight getFlight(int fid) throws SQLException {
    searchFIDStatement.clearParameters();
    searchFIDStatement.setInt(1, fid);
    ResultSet rs = searchFIDStatement.executeQuery();
    rs.next();
    Flight flight = new Flight();
    setFlight(flight, rs, "");
    return flight;
  }

  private void setFlight(Flight flight, ResultSet rs, String prefix) throws SQLException {
    flight.fid = rs.getInt(prefix + "fid");
    flight.dayOfMonth = rs.getInt(prefix + "day_of_month");
    flight.carrierId = rs.getString(prefix + "carrier_id");
    flight.flightNum = rs.getString(prefix + "flight_num");
    flight.originCity = rs.getString(prefix + "origin_city");
    flight.destCity = rs.getString(prefix + "dest_city");
    flight.time = rs.getInt(prefix + "actual_time");
    flight.capacity = rs.getInt(prefix + "capacity");
    flight.price = rs.getInt(prefix + "price");
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   * all other errors, return "Failed to cancel reservation [reservationId]\n"
   * <p>
   * If successful, return "Canceled reservation [reservationId]\n"
   * <p>
   * Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    if (!isLogin) {
      return "Cannot cancel reservations, not logged in\n";
    }
    try {
      selectReservationStatement.clearParameters();
      selectReservationStatement.setInt(1, reservationId);
      ResultSet rs = selectReservationStatement.executeQuery();
      if (!rs.next()) {
        return "Failed to cancel reservation " + reservationId + "\n";
      }
      // select isPaid
      // check if the reservation is paid,
      // yes, refund the money back to the user
      int isPaid = rs.getInt("IsPaid");
      if (isPaid == 1) {
        // refund
        // update user table added with itinerary total price
        int refund = getTotalTicketPrice(rs);
        selectUserNameStatement.clearParameters();
        selectUserNameStatement.setString(1, loginUserName);
        ResultSet rss = selectUserNameStatement.executeQuery();
        rss.next();
        int balance = rss.getInt("Balance");
        // UPDATE Users SET Balance = ?
        updateBalance(balance + refund);
      }
      // update the reservation table where reservationId = reservationId
      // UPDATE Reservations SET IsCancelled = 1 WHERE reservationID = ?
      updateCancelReservation(reservationId);
      return "Canceled reservation " + reservationId + "\n";
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return "Failed to cancel reservation " + reservationId + "\n";
  }

  private void updateCancelReservation(int reservationId) throws SQLException {
    updateCancelReservationStatement.clearParameters();
    updateCancelReservationStatement.setInt(1, reservationId);
    updateReservationStatement.executeUpdate();
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();
    return capacity;
  }

  public static boolean isDeadLock(SQLException ex) {
    return ex.getErrorCode() == 1205;
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }
  class Itinerary {
    public Flight flight1;
    public Flight flight2;

    public Itinerary(Flight flight1, Flight flight2) {
      this.flight1 = flight1;
      this.flight2 = flight2;
    }
    public int getTotalTime() {
      if (flight2 == null) {
        return flight1.time;
      }
      return flight1.time + flight2.time;
    }
  }
}

# Flight App (CSE344)

To test cases folder recursively (all ``.txt`` files):

```
mvn test
```

To test a specific folder recursively (5 depths) or a specific file:

```
mvn test -Dtest.cases="[folder/file]"
```

You can also run multiple folder/file as one command with `:` separator:
```
mvn test -Dtest.cases="[/path/to/file:/path/to/folder]"
```

To test with a specific server credential without dbconn.properties:

```
mvn test -Dcredential="[server_URL];[db_name];[username];[password]"
```

To test with grading server credential without dbconn.properties (ask instructors/TAs for host port, may
or may not be available):

```
mvn test -DcredentialServer="[hostname]:[port]"
```

To run flight app:

```
mvn compile exec:java
```

To build jar with dependency and then run that jar:

```
mvn compile assembly:single

java -jar target/FlightApp-1.0-jar-with-dependencies.jar
```

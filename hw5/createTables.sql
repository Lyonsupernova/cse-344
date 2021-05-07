CREATE TABLE Users (
UserName VARCHAR(20) PRIMARY KEY,
Password VARBINARY(20),
Balance INT,
Salt VARBINARY(16)
);

CREATE TABLE Itineraries (
ItineraryID INT PRIMARY KEY,
fid1 INT,
fid2 INT NULL,
FOREIGN KEY (fid1) REFERENCES FLIGHTS (fid),
FOREIGN KEY (fid2) REFERENCES FLIGHTS (fid)
);

CREATE TABLE Reservations (
ReservationID INT PRIMARY KEY,
IsPaid INT,
IsCancelled INT,
ItineraryID INT,
UserName VARCHAR(20),
FOREIGN KEY (ItineraryID) REFERENCES Itineraries (ItineraryID),
FOREIGN KEY (UserName) REFERENCES Users (UserName)
);

CREATE TABLE Capacity (
fid INT PRIMARY KEY,
capacity INT,
FOREIGN KEY (fid) REFERENCES FLIGHTS (fid)
)

INSERT INTO Capacity (fid, capacity)
SELECT fid, capacity FROM FLIGHTS;
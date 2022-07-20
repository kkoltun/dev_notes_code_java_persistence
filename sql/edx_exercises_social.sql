/* Delete the tables if they already exist */
drop table if exists Highschooler;
drop table if exists Friend;
drop table if exists Likes;

/* Create the schema for our tables */
create table Highschooler
(
    ID    int,
    name  text,
    grade int
);
create table Friend
(
    ID1 int,
    ID2 int
);
create table Likes
(
    ID1 int,
    ID2 int
);

/* Populate the tables with our data */
insert into Highschooler
values (1510, 'Jordan', 9);
insert into Highschooler
values (1689, 'Gabriel', 9);
insert into Highschooler
values (1381, 'Tiffany', 9);
insert into Highschooler
values (1709, 'Cassandra', 9);
insert into Highschooler
values (1101, 'Haley', 10);
insert into Highschooler
values (1782, 'Andrew', 10);
insert into Highschooler
values (1468, 'Kris', 10);
insert into Highschooler
values (1641, 'Brittany', 10);
insert into Highschooler
values (1247, 'Alexis', 11);
insert into Highschooler
values (1316, 'Austin', 11);
insert into Highschooler
values (1911, 'Gabriel', 11);
insert into Highschooler
values (1501, 'Jessica', 11);
insert into Highschooler
values (1304, 'Jordan', 12);
insert into Highschooler
values (1025, 'John', 12);
insert into Highschooler
values (1934, 'Kyle', 12);
insert into Highschooler
values (1661, 'Logan', 12);

insert into Friend
values (1510, 1381);
insert into Friend
values (1510, 1689);
insert into Friend
values (1689, 1709);
insert into Friend
values (1381, 1247);
insert into Friend
values (1709, 1247);
insert into Friend
values (1689, 1782);
insert into Friend
values (1782, 1468);
insert into Friend
values (1782, 1316);
insert into Friend
values (1782, 1304);
insert into Friend
values (1468, 1101);
insert into Friend
values (1468, 1641);
insert into Friend
values (1101, 1641);
insert into Friend
values (1247, 1911);
insert into Friend
values (1247, 1501);
insert into Friend
values (1911, 1501);
insert into Friend
values (1501, 1934);
insert into Friend
values (1316, 1934);
insert into Friend
values (1934, 1304);
insert into Friend
values (1304, 1661);
insert into Friend
values (1661, 1025);
insert into Friend
select ID2, ID1
from Friend;

insert into Likes
values (1689, 1709);
insert into Likes
values (1709, 1689);
insert into Likes
values (1782, 1709);
insert into Likes
values (1911, 1247);
insert into Likes
values (1247, 1468);
insert into Likes
values (1641, 1468);
insert into Likes
values (1316, 1304);
insert into Likes
values (1501, 1934);
insert into Likes
values (1934, 1501);
insert into Likes
values (1025, 1101);

SELECT *
FROM Highschooler H1,
     Likes L,
     Highschooler H2
WHERE H1.ID = L.ID1
  AND H2.ID = L.ID2
  AND H1.name < H2.name
  AND EXISTS(SELECT * FROM Likes L2 WHERE H1.ID = L2.ID2 AND H2.ID = L2.ID1)

SELECT name, grade
FROM Highschooler H
         LEFT JOIN Likes L ON H.ID = L.ID1 OR H.ID = L.ID2
WHERE L.ID1 IS NULL
  AND L.ID2 IS NULL

SELECT *
FROM Highschooler H
WHERE ID NOT IN (SELECT H1.ID
                 FROM Highschooler H1,
                      Friend F,
                      Highschooler H2
                 WHERE H1.ID = F.ID1
                   AND H2.ID = F.ID2
                   AND H1.grade != H2.grade
                 UNION
                 SELECT H2.ID
                 FROM Highschooler H1,
                      Friend F,
                      Highschooler H2
                 WHERE H1.ID = F.ID1
                   AND H2.ID = F.ID2
                   AND H1.grade != H2.grade);

SELECT H1.name, H1.grade, H2.name, H2.grade, FH1.name, FH1.grade
FROM Highschooler H1,
     Likes L,
     Highschooler H2
         JOIN Friend F1 ON F1.ID1 = H1.ID
         JOIN Highschooler FH1 ON FH1.ID = F1.ID2
         JOIN Friend F2 ON F2.ID1 = H2.ID
         JOIN Highschooler FH2 ON FH2.ID = F2.ID2
WHERE H1.ID = L.ID1
  AND H2.ID = L.ID2
  AND NOT EXISTS(SELECT * FROM Friend F WHERE F.ID1 = H1.ID AND F.ID2 = H2.ID)
  AND FH1.ID = FH2.ID;

SELECT (SELECT COUNT(*) FROM Highschooler) - (SELECT COUNT(DISTINCT name) FROM Highschooler)

SELECT H.name, H.grade
FROM Highschooler H
         JOIN Likes L ON H.ID = L.ID2
GROUP BY H.ID
HAVING COUNT(*) > 1;

SELECT *
FROM Likes
WHERE EXISTS(SELECT * FROM Friend WHERE Friend.ID1 = Likes.ID1 AND Friend.ID2 = Likes.ID2)
  AND NOT EXISTS(SELECT * FROM Likes L2 WHERE Likes.ID1 = L2.ID2 AND Likes.ID2 = L2.ID1)


SELECT DISTINCT H1.ID, H2.ID
FROM Highschooler H1,
     Highschooler H2
         JOIN Friend F1 ON F1.ID1 = H1.ID
         JOIN Highschooler FH1 ON FH1.ID = F1.ID2
         JOIN Friend F2 ON F2.ID1 = H2.ID
         JOIN Highschooler FH2 ON FH2.ID = F2.ID2
WHERE H1.ID != H2.ID
  AND NOT EXISTS (SELECT * FROM Friend F WHERE F.ID1 = H1.ID AND F.ID2 = H2.ID)
  AND FH1.ID = FH2.ID
/* Delete the tables if they already exist */
drop table if exists Movie;
drop table if exists Reviewer;
drop table if exists Rating;

/* Create the schema for our tables */
create table Movie
(
    mID      int,
    title    text,
    year     int,
    director text
);
create table Reviewer
(
    rID  int,
    name text
);
create table Rating
(
    rID        int,
    mID        int,
    stars      int,
    ratingDate date
);

/* Populate the tables with our data */
insert into Movie
values (101, 'Gone with the Wind', 1939, 'Victor Fleming');
insert into Movie
values (102, 'Star Wars', 1977, 'George Lucas');
insert into Movie
values (103, 'The Sound of Music', 1965, 'Robert Wise');
insert into Movie
values (104, 'E.T.', 1982, 'Steven Spielberg');
insert into Movie
values (105, 'Titanic', 1997, 'James Cameron');
insert into Movie
values (106, 'Snow White', 1937, null);
insert into Movie
values (107, 'Avatar', 2009, 'James Cameron');
insert into Movie
values (108, 'Raiders of the Lost Ark', 1981, 'Steven Spielberg');

insert into Reviewer
values (201, 'Sarah Martinez');
insert into Reviewer
values (202, 'Daniel Lewis');
insert into Reviewer
values (203, 'Brittany Harris');
insert into Reviewer
values (204, 'Mike Anderson');
insert into Reviewer
values (205, 'Chris Jackson');
insert into Reviewer
values (206, 'Elizabeth Thomas');
insert into Reviewer
values (207, 'James Cameron');
insert into Reviewer
values (208, 'Ashley White');

insert into Rating
values (201, 101, 2, '2011-01-22');
insert into Rating
values (201, 101, 4, '2011-01-27');
insert into Rating
values (201, 101, 3, '2012-01-27');
insert into Rating
values (202, 106, 4, null);
insert into Rating
values (203, 103, 2, '2011-01-20');
insert into Rating
values (203, 108, 4, '2011-01-12');
insert into Rating
values (203, 108, 5, '2012-01-12');
insert into Rating
values (203, 108, 2, '2011-01-30');
insert into Rating
values (204, 101, 3, '2011-01-09');
insert into Rating
values (205, 103, 3, '2011-01-27');
insert into Rating
values (205, 104, 2, '2011-01-22');
insert into Rating
values (205, 108, 4, null);
insert into Rating
values (206, 107, 3, '2011-01-15');
insert into Rating
values (206, 106, 5, '2011-01-19');
insert into Rating
values (207, 107, 5, '2011-01-20');
insert into Rating
values (208, 104, 3, '2011-01-02');
insert into Rating
values (208, 104, 4, '2012-01-02');
insert into Rating
values (208, 104, 5, '2013-01-02');

SELECT name, title, stars, ratingDate
FROM Movie,
     Reviewer,
     Rating
WHERE Movie.mID = Rating.mID
  AND Rating.rID = Reviewer.rID
ORDER BY name, title, ratingDate, stars;

SELECT M.title, REV.name
FROM Rating R1,
     Rating R2,
     Movie M,
     Reviewer REV
WHERE R1.mID = R2.mID
  AND R1.rID = R2.rID
  AND R1.stars > R2.stars
  AND R1.ratingDate > R2.ratingDate
  AND M.mID = R1.mID
  AND REV.rID = R1.rID;

SELECT *
FROM Rating RAT,
     Movie M,
     Reviewer R,
     (SELECT rID, mID FROM Rating R GROUP BY R.rID, R.mID HAVING COUNT(*) > 1) MULTIPLE_R
WHERE RAT.stars = (SELECT MAX(stars) FROM Rating R WHERE R.rID = RAT.rID AND R.mID = RAT.mID)
  AND RAT.ratingDate = (SELECT MAX(ratingDate) FROM Rating R WHERE R.rID = RAT.rID AND R.mID = RAT.mID)
  AND RAT.rID = MULTIPLE_R.rID
  AND RAT.mID = MULTIPLE_R.mID
  AND RAT.rID = R.rID
  AND RAT.mID = M.mID;

SELECT name, title
FROM (SELECT X.rID, X.mID, minRating.ratingDate AS minRatingDate, maxRating.ratingDate AS maxRatingDate
      FROM (SELECT Rating.rID as rid, Rating.mID AS mid, MAX(stars) max_stars, MIN(stars) min_stars
            FROM Rating
            GROUP BY Rating.rID, Rating.mID
            HAVING COUNT(*) > 1) X,
           Rating maxRating,
           Rating minRating
      WHERE maxRating.rID = X.rID
        AND maxRating.mID = X.mID
        AND maxRating.stars = max_stars
        AND minRating.rID = X.rID
        AND minRating.mID = X.mID
        AND minRating.stars = min_stars) Y,
     Reviewer,
     Movie
WHERE Y.rID = Reviewer.rID
  AND Y.mID = Movie.mID
  AND minRatingDate < maxRatingDate;

SELECT (SELECT AVG(average)
        FROM (SELECT AVG(RT.stars) AS average, M.year
              FROM Rating RT,
                   Movie M
              WHERE RT.mID = M.mID
              GROUP BY M.mID)
        WHERE year > 1980) - (SELECT AVG(average)
                              FROM (SELECT AVG(RT.stars) AS average, M.year
                                    FROM Rating RT,
                                         Movie M
                                    WHERE RT.mID = M.mID
                                    GROUP BY M.mID)
                              WHERE year < 1980);

UPDATE Movie
SET year = year + 25
WHERE (SELECT AVG(stars) FROM Rating R WHERE mID = R.mID GROUP BY R.mID) >= 4;

SELECT M.*, AVG(stars)
FROM Movie M,
     Rating R
WHERE M.mID = R.mID
GROUP BY R.mID
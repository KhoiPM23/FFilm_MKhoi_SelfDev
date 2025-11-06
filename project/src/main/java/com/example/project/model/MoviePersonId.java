// src/main/java/com/example/project/model/MoviePersonId.java
package com.example.project.model;

import java.io.Serializable;

public class MoviePersonId implements Serializable {
    private int movieID;
    private int personID;

    // Constructors
    public MoviePersonId() {
    }

    public MoviePersonId(int movieID, int personID) {
        this.movieID = movieID;
        this.personID = personID;
    }

    // equals() & hashCode()
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MoviePersonId))
            return false;
        MoviePersonId that = (MoviePersonId) o;
        return movieID == that.movieID && personID == that.personID;
    }

    @Override
    public int hashCode() {
        return 31 * movieID + personID;
    }
}
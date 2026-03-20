package software.decibel.utils;

import org.springframework.stereotype.Component;

@Component
public class LocationUtility {

    // Builds a location string from city and country
    // ex: "Cairo, Egypt"
    public String buildLocation(String city, String country) {
        if (city == null && country == null) return null;
        if (city == null) return country;
        if (country == null) return city;
        return city + ", " + country;
    }

    // Parses city from location string
    public String parseCity(String location) {
        if (location == null) return null;
        String[] parts = location.split(",", 2);
        return parts[0].trim();
    }

    // Parses country from location string
    public String parseCountry(String location) {
        if (location == null) return null;
        String[] parts = location.split(",", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }
}
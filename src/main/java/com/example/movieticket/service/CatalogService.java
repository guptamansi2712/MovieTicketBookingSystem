package com.example.movieticket.service;

import com.example.movieticket.repository.CatalogRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final CatalogRepository catalogRepository;

    public CatalogService(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    public List<Map<String, Object>> cities() {
        return catalogRepository.findCities();
    }

    public List<Map<String, Object>> shows(Long cityId) {
        return catalogRepository.findShows(cityId);
    }

    public List<Map<String, Object>> availability(long showId) {
        return catalogRepository.findAvailability(showId);
    }
}

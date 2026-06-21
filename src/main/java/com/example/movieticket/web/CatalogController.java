package com.example.movieticket.web;

import com.example.movieticket.service.CatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/cities")
    List<Map<String, Object>> cities() {
        return catalogService.cities();
    }

    @GetMapping("/shows")
    List<Map<String, Object>> shows(@RequestParam(required = false) Long cityId) {
        return catalogService.shows(cityId);
    }

    @GetMapping("/shows/{showId}/seats")
    List<Map<String, Object>> availability(@PathVariable long showId) {
        return catalogService.availability(showId);
    }
}

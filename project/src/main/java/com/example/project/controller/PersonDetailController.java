package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.model.MoviePerson;
import com.example.project.repository.MoviePersonRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.service.MovieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class PersonDetailController {

    @Autowired
    private MovieService movieService;
    
    @Autowired
    private MoviePersonRepository moviePersonRepository;
    
    @Autowired
    private MovieRepository movieRepository;

    @GetMapping("/person/detail/{id}")
    public String personDetail(@PathVariable("id") int personId, Model model) {
        
        Person person = movieService.getPersonByIdOrSync(personId);
        if (person == null) {
            return "redirect:/"; 
        }

        // [FIX] DÙNG MoviePersonRepository thay vì person.getMovies()
        List<MoviePerson> mps = moviePersonRepository.findByPersonID(personId);
        List<Map<String, Object>> moviesMapList = new ArrayList<>();

        for (MoviePerson mp : mps) {
            Movie movie = movieRepository.findById(mp.getMovieID()).orElse(null);
            if (movie != null) {
                Map<String, Object> movieMap = movieService.convertToMap(movie);
                
                // Lấy role từ MoviePerson (ưu tiên character > job)
                String role = (mp.getCharacterName() != null && !mp.getCharacterName().isEmpty()) 
                              ? "Vai: " + mp.getCharacterName()
                              : (mp.getJob() != null ? mp.getJob() : "Diễn viên");
                
                movieMap.put("role_info", role);
                moviesMapList.add(movieMap);
            }
        }

        // Sắp xếp mới nhất lên đầu
        moviesMapList.sort((m1, m2) -> {
            String date1 = (String) m1.getOrDefault("releaseDate", "0000-00-00");
            String date2 = (String) m2.getOrDefault("releaseDate", "0000-00-00");
            return date2.compareTo(date1); 
        });

        model.addAttribute("person", movieService.convertToMap(person));
        model.addAttribute("movies", moviesMapList);
        model.addAttribute("clientSideLoad", false); 

        return "person/person-detail";
    }
}
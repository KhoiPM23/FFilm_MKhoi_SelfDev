package com.example.project.controller;

import com.example.project.model.Movie;
import com.example.project.model.Person;

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
    


    @GetMapping("/person/detail/{id}")
    public String personDetail(@PathVariable("id") int personId, Model model) {
        
        Person person = movieService.getPersonByIdOrSync(personId);
        if (person == null) {
            return "redirect:/"; 
        }

        // [FIX] DÙNG MovieService.getMoviesMapByPersonId() thay vì Controller query Repository
        List<Map<String, Object>> moviesMapList = movieService.getMoviesMapByPersonId(personId);

        model.addAttribute("person", movieService.convertToMap(person));
        model.addAttribute("movies", moviesMapList);
        model.addAttribute("clientSideLoad", false); 

        return "person/person-detail";
    }
}
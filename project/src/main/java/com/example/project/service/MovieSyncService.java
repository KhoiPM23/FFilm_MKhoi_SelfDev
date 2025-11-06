
package com.example.project.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.project.model.Category;
import com.example.project.model.Episode;
import com.example.project.model.Movie;
import com.example.project.model.Person;
import com.example.project.model.Season;
import com.example.project.repository.CategoryRepository;
import com.example.project.repository.EpisodeRepository;
import com.example.project.repository.MovieRepository;
import com.example.project.repository.PersonRepository;
import com.example.project.repository.SeasonRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MovieSyncService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private final MovieRepository movieRepository;
    private final CategoryRepository categoryRepository;
    private final PersonRepository personRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;

    private final String API_KEY = "eac03c4e09a0f5099128e38cb0e67a8f";
    private final String BASE_URL = "https://api.themoviedb.org/3";

    public MovieSyncService(MovieRepository movieRepository,
            CategoryRepository categoryRepository,
            PersonRepository personRepository,
            SeasonRepository seasonRepository,
            EpisodeRepository episodeRepository) {
        this.movieRepository = movieRepository;
        this.categoryRepository = categoryRepository;
        this.personRepository = personRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
    }

    // === CHỈ LÀM 1 VIỆC: SYNC 1 PHIM ===
    public void syncMovieFromTmdb(int tmdbId) throws Exception {
        String url = BASE_URL + "/movie/" + tmdbId + "?api_key=" + API_KEY + "&append_to_response=credits";
        String json = restTemplate.getForObject(url, String.class);
        JsonNode root = mapper.readTree(json);

        // 1. Kiểm tra trùng title
        String title = root.get("title").asText();
        if (movieRepository.findByTitle(title).isPresent()) {
            return; // đã có → bỏ qua
        }

        // 2. Tạo Movie
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setDescription(root.get("overview").asText());
        movie.setReleaseDate(parseDate(root.get("release_date").asText()));
        movie.setFree(true); // mặc định
        movie.setDuration(root.get("runtime").asInt());
        movie.setRating((float) root.get("vote_average").asDouble());
        movie.setUrl("https://image.tmdb.org/t/p/w500" + root.path("poster_path").asText());

        // 3. Category (N-N)
        List<Category> categories = new ArrayList<>();
        for (JsonNode genre : root.get("genres")) {
            String name = genre.get("name").asText();
            Category cat = categoryRepository.findByName(name)
                    .orElseGet(() -> {
                        Category c = new Category();
                        c.setName(name);
                        c.setCategoryParentID(null);
                        return categoryRepository.save(c);
                    });
            categories.add(cat);
        }
        movie.setCategories(categories);

        // 4. Person (N-N) – chỉ lấy diễn viên + đạo diễn
        List<Person> persons = new ArrayList<>();

        // Diễn viên
        JsonNode cast = root.path("credits").path("cast");
        for (JsonNode c : cast) {
            if (persons.size() >= 10)
                break;
            String name = c.get("name").asText();
            Person p = personRepository.findByFullName(name)
                    .orElseGet(() -> {
                        Person np = new Person();
                        np.setFullName(name);
                        np.setBio("No bio");
                        np.setType("Actor");
                        return personRepository.save(np);
                    });
            persons.add(p);
        }

        // Đạo diễn
        JsonNode crew = root.path("credits").path("crew");
        for (JsonNode c : crew) {
            if ("Director".equals(c.get("job").asText())) {
                String name = c.get("name").asText();
                Person p = personRepository.findByFullName(name)
                        .orElseGet(() -> {
                            Person np = new Person();
                            np.setFullName(name);
                            np.setBio("No bio");
                            np.setType("Director");
                            return personRepository.save(np);
                        });
                persons.add(p);
                break;
            }
        }
        movie.setPersons(persons);

        // 5. Lưu Movie (cascade sẽ lưu Category, Person nếu cần)
        movieRepository.save(movie);
    }

    // === SYNC SERIES (TV) ===
    public void syncTvShowFromTmdb(int tmdbId) throws Exception {
        String url = BASE_URL + "/tv/" + tmdbId + "?api_key=" + API_KEY;
        String json = restTemplate.getForObject(url, String.class);
        JsonNode tv = mapper.readTree(json);

        String title = tv.get("name").asText();
        if (movieRepository.findByTitle(title).isPresent())
            return;

        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setDescription(tv.get("overview").asText());
        movie.setReleaseDate(parseDate(tv.get("first_air_date").asText()));
        movie.setFree(true);
        movie.setDuration(0); // series không có runtime chung
        movie.setRating((float) tv.get("vote_average").asDouble());
        movie.setUrl("https://image.tmdb.org/t/p/w500" + tv.path("poster_path").asText());

        // Genre
        List<Category> categories = new ArrayList<>();
        for (JsonNode g : tv.get("genres")) {
            String name = g.get("name").asText();
            Category cat = categoryRepository.findByName(name)
                    .orElseGet(() -> {
                        Category c = new Category();
                        c.setName(name);
                        return categoryRepository.save(c);
                    });
            categories.add(cat);
        }
        movie.setCategories(categories);

        // Lưu movie trước để có ID
        movie = movieRepository.save(movie);

        // === Sync Seasons & Episodes ===
        for (JsonNode seasonNode : tv.get("seasons")) {
            int seasonNum = seasonNode.get("season_number").asInt();
            if (seasonNum == 0)
                continue; // bỏ Specials

            Season season = new Season();
            season.setSeasonNumber(seasonNum);
            season.setTitle(seasonNode.get("name").asText());
            season.setReleaseDate(parseDate(seasonNode.get("air_date").asText()));
            season.setMovie(movie);

            // Lấy tập
            String epUrl = BASE_URL + "/tv/" + tmdbId + "/season/" + seasonNum + "?api_key=" + API_KEY;
            String epJson = restTemplate.getForObject(epUrl, String.class);
            JsonNode epRoot = mapper.readTree(epJson);

            List<Episode> episodes = new ArrayList<>();
            for (JsonNode ep : epRoot.get("episodes")) {
                Episode episode = new Episode();
                episode.setTitle(ep.get("name").asText());
                episode.setReleaseDate(parseDate(ep.get("air_date").asText()));
                episode.setVideoUrl("placeholder.mp4"); // bạn sẽ thay sau
                episode.setSeason(season);
                episodes.add(episode);
            }
            season.setEpisodes(episodes);
            seasonRepository.save(season); // cascade lưu Episode
        }
    }

    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty())
            return new Date();
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
        } catch (Exception e) {
            return new Date();
        }
    }
}
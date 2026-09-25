# FFilm - Social Movie Discovery Platform 🎬

**FFilm** is a comprehensive web application designed for movie enthusiasts. It combines movie discovery with social interaction, allowing users to search for films, watch trailers, chat in real-time, and host "Watch Parties" to enjoy content together synchronously.

> **Status:** Active Development 🚀
> **Demo:** [Link to your video demo or live site if available]

---

## ✨ Key Features

### 🎥 Discovery & Streaming
* **Smart Search (AI-Powered):** Integrated AI Agent (`AIAgentService`) to help users find movies based on natural language queries / context.
* **Rich Metadata:** Fetches up-to-date movie details, posters, casts, and trailers via **TMDB API**.
* **Personalization:** Users can build "Favorites" lists and track their "Watch History".

### 🤝 Social & Real-time Interaction
* **Watch Party:** Create private rooms to watch movies with friends. Video playback is synchronized in real-time across all members using **WebSocket**.
* **Live Chat:** Real-time messaging system (Messenger) with sticker support and notifications.
* **Community:** Friend system (Add/Accept/Block), public profiles, and activity feeds.

### 💳 Monetization & System
* **Subscription Plans:** Tiered membership system (Manage Plans).
* **Payment Integration:** Secure payment processing via **VnPay**.
* **Admin Dashboard:** Comprehensive dashboard for managing movies, users, comments, and revenue statistics.

---

## 🛠️ Tech Stack

**Backend:**
* **Java 17**
* **Spring Boot 3.x** (Spring Security, Spring Data JPA, Spring MVC)
* **WebSocket (STOMP)** for real-time communication
* **SQL Server** Database

**Frontend:**
* **Thymeleaf** (Server-side rendering)
* **HTML5 / CSS3 / JavaScript**
* **Bootstrap** for responsive design

**Integrations & APIs:**
* **The Movie Database (TMDB) API**
* **OpenAI / Gemini API** (for AI Search features)
* **VnPay SDK** (Payment Gateway)

---

## 📸 Screenshots

| Home Page | Watch Party Room |
|:---:|:---:|
| ![Home](https://via.placeholder.com/400x200?text=Home+Page+Screenshot) | ![WatchParty](https://via.placeholder.com/400x200?text=Watch+Party+Screenshot) |

| AI Search | Admin Dashboard |
|:---:|:---:|
| ![AI](https://via.placeholder.com/400x200?text=AI+Search+Screenshot) | ![Admin](https://via.placeholder.com/400x200?text=Admin+Dashboard+Screenshot) |

---

## 🚀 Getting Started

### Prerequisites
* Java Development Kit (JDK) 17 or higher
* Maven
* SQL Server

### Installation

1.  **Clone the repository**
    ```bash
    git clone https://github.com/KhoiPM23/FFilm_MKhoi_SelfDev.git
    cd FFilm_MKhoi_SelfDev/project
    ```

2.  **Database Configuration**
    * Create a SQL Server database named `FFilm3`.
    * Update database credentials in `application.properties.example` and rename it to `application.properties`:
    ```properties
    spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=FFilm3;encrypt=true;trustServerCertificate=true;
    spring.datasource.username=sa
    spring.datasource.password=123
    ```

3.  **API Keys Setup**
    * Get a generic API Key from [TMDB](https://www.themoviedb.org/).
    * Configure your Gemini Provider Key, Tenor Key, and VnPay credentials in `application.properties`.

4.  **Run the Application**
    ```bash
    .\mvnw.cmd spring-boot:run
    ```
    The app will start at `http://localhost:8081`.

---

## 🛡️ License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**[Phan Minh Khôi]**
* **Github:** [Check out my projects](https://github.com/KhoiPM23)
* **Email:** [Your Email Here]
* **LinkedIn:** [Your LinkedIn Profile]

---

*Note: This project is for educational purposes and portfolio demonstration.*
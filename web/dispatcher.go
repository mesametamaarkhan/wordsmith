package main

import (
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"time"
)

func main() {
	apiHost := envOrDefault("WORDS_HOST", "api")
	apiPort := envAsInt("WORDS_PORT", 8080)
	webPort := envAsInt("WEB_PORT", 8080)

	fwd := &forwarder{host: apiHost, port: apiPort}
	http.Handle("/words/", http.StripPrefix("/words", fwd))
	http.Handle("/", http.FileServer(http.Dir("static")))

	addr := fmt.Sprintf(":%d", webPort)
	fmt.Printf("Listening on port %d and forwarding to %s:%d\n", webPort, apiHost, apiPort)
	server := &http.Server{
		Addr:              addr,
		ReadHeaderTimeout: 5 * time.Second,
	}

	log.Fatal(server.ListenAndServe())
}

type forwarder struct {
	host string
	port int
}

func (f *forwarder) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	url := fmt.Sprintf("http://%s:%d%s", f.host, f.port, r.URL.Path)
	if r.URL.RawQuery != "" {
		url = fmt.Sprintf("%s?%s", url, r.URL.RawQuery)
	}
	log.Printf("%s Calling %s", r.URL.Path, url)

	if err := copy(url, w); err != nil {
		log.Println("Error", err)
		http.Error(w, err.Error(), 500)
		return
	}
}

func copy(url string, w http.ResponseWriter) error {
	resp, err := http.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	for header, values := range resp.Header {
		for _, value := range values {
			w.Header().Add(header, value)
		}
	}
	w.WriteHeader(resp.StatusCode)

	buf, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}

	_, err = w.Write(buf)
	return err
}

func envOrDefault(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}

	return fallback
}

func envAsInt(name string, fallback int) int {
	raw := os.Getenv(name)
	if raw == "" {
		return fallback
	}

	value, err := strconv.Atoi(raw)
	if err != nil {
		log.Printf("invalid integer for %s=%q, using default %d", name, raw, fallback)
		return fallback
	}

	return value
}

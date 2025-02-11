package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"testing"
)

type CopyRequest struct {
	Text       string `json:"text"`
	TTL        int    `json:"ttl"`
}

type CopyResponse struct {
	ID string `json:"id"`
}

type PasteResponse struct {
	Text string `json:"text"`
}

type ErrorResponse struct {
	ErrorMessage string `json:"message"`
}

const serverURL = "http://localhost:8080"

func copyText(text string, ttl int) (string, error) {
	if len(text) > 1000 || ttl > 600 {
		return "", errors.New("invalid input: text must be <1000 chars, TTL <= 600s")
	}
	
	data, _ := json.Marshal(CopyRequest{Text: text, TTL: ttl})
	resp, err := http.Post(serverURL+"/copy", "application/json", bytes.NewReader(data))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	
	if resp.StatusCode != http.StatusOK {
		return "", errors.New("failed to copy text")
	}
	var result CopyResponse
	json.NewDecoder(resp.Body).Decode(&result)
	return result.ID, nil
}

func pasteText(id string) (string, error) {
	resp, err := http.Get(fmt.Sprintf("%s/paste/%s", serverURL, id))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		var errResp ErrorResponse
		json.NewDecoder(resp.Body).Decode(&errResp)
		return "", errors.New(errResp.ErrorMessage)
	}

	var result PasteResponse
	json.NewDecoder(resp.Body).Decode(&result)
	return result.Text, nil
}

func TestCopyText(t *testing.T) {
	id, err := copyText("test", 300)
	if err != nil {
		t.Errorf("Expected no error, got %v", err)
	}
	if id == "" {
		t.Errorf("Expected valid ID, got empty string")
	}
}

func TestPasteText(t *testing.T) {
	id, _ := copyText("test", 300)
	text, err := pasteText(id)
	if err != nil {
		t.Errorf("Expected no error, got %v", err)
	}
	if text != "test" {
		t.Errorf("Expected 'test', got %v", text)
	}
}

func main() {
	if len(os.Args) < 3 {
		fmt.Println("Usage: gcv -c \"text\" TTL | gcv -v ID")
		return
	}

	cmd := os.Args[1]
	if cmd == "-c" && len(os.Args) == 4 {
		text := os.Args[2]
		ttl := 600 // default TTL
		fmt.Sscanf(os.Args[3], "%d", &ttl)
		id, err := copyText(text, ttl)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		fmt.Printf("id: %s, ttl: %ds\n", id, ttl)
	} else if cmd == "-v" && len(os.Args) == 3 {
		id := os.Args[2]
		text, err := pasteText(id)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		fmt.Println(text)
	} else {
		fmt.Println("Invalid command usage")
	}
}

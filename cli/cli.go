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
	PasteLimit *int   `json:"pasteLimit,omitempty"`
}

type CopyResponse struct {
	ID string `json:"id"`
}

type PasteResponse struct {
	Text string `json:"text"`
	Qr   string `json:"qr"`
}

type ErrorResponse struct {
	ErrorMessage string `json:"message"`
}

const serverURL = "http://localhost:8080"

func copyText(text string, ttl int, pasteLimit *int) (string, error) {
	if len(text) > 1000 || ttl > 600 {
		return "", errors.New("invalid input: text must be <1000 chars, TTL <= 600s")
	}
	if pasteLimit != nil && *pasteLimit <= 0 {
		return "", errors.New("invalid input: paste limit must be > 0")
	}

	data, _ := json.Marshal(CopyRequest{Text: text, TTL: ttl, PasteLimit: pasteLimit})
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

func pasteText(id string) (*PasteResponse, error) {
	resp, err := http.Get(fmt.Sprintf("%s/paste/%s", serverURL, id))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		var errResp ErrorResponse
		json.NewDecoder(resp.Body).Decode(&errResp)
		return nil, errors.New(errResp.ErrorMessage)
	}

	var result PasteResponse
	json.NewDecoder(resp.Body).Decode(&result)
	return &result, nil
}

func TestCopyText(t *testing.T) {
	id, err := copyText("test", 300, nil)
	if err != nil {
		t.Errorf("Expected no error, got %v", err)
	}
	if id == "" {
		t.Errorf("Expected valid ID, got empty string")
	}
}

func TestPasteText(t *testing.T) {
	id, _ := copyText("test", 300, nil)
	text, err := pasteText(id)
	if err != nil {
		t.Errorf("Expected no error, got %v", err)
	}
	if text.Text != "test" {
		t.Errorf("Expected 'test', got %v", text)
	}
}

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Usage: gcv -c \"text\" [TTL] [PASTE_LIMIT] | gcv -v ID")
		return
	}

	cmd := os.Args[1]
	if cmd == "-c" {
		if len(os.Args) < 3 {
			fmt.Println("Error: text argument required")
			return
		}
		text := os.Args[2]
		ttl := 600 // default TTL
		if len(os.Args) >= 4 {
			fmt.Sscanf(os.Args[3], "%d", &ttl)
		}
		var pasteLimit *int
		if len(os.Args) == 5 {
			var n int
			if _, err := fmt.Sscanf(os.Args[4], "%d", &n); err != nil {
				fmt.Println("Error: invalid paste limit")
				return
			}
			pasteLimit = &n
		}
		id, err := copyText(text, ttl, pasteLimit)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		if pasteLimit != nil {
			fmt.Printf("id: %s, ttl: %ds, pasteLimit: %d\n", id, ttl, *pasteLimit)
		} else {
			fmt.Printf("id: %s, ttl: %ds, pasteLimit: unlimited\n", id, ttl)
		}
	} else if cmd == "-v" && len(os.Args) == 3 {
		id := os.Args[2]
		text, err := pasteText(id)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		fmt.Println(text.Text)
	} else if cmd == "-qr" && len(os.Args) == 3 {
		id := os.Args[2]
		text, err := pasteText(id)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		fmt.Println(text.Qr)
	} else {
		fmt.Println("Invalid command usage")
	}
}

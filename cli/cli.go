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
	if pasteLimit != nil && *pasteLimit < 1 {
		return "", errors.New("invalid input: paste limit must be >= 1")
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
		fmt.Println("Usage: gcv -c \"text\" [TTL] [-l LIMIT] | gcv -v ID")
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
		var pasteLimit *int

		extra := os.Args[3:]
		i := 0
		for i < len(extra) {
			arg := extra[i]
			if arg == "-l" {
				if i+1 >= len(extra) {
					fmt.Println("Error: -l requires a numeric value")
					return
				}
				var limit int
				if _, err := fmt.Sscanf(extra[i+1], "%d", &limit); err != nil {
					fmt.Println("Error: -l requires a numeric value")
					return
				}
				pasteLimit = &limit
				i += 2
			} else {
				if _, err := fmt.Sscanf(arg, "%d", &ttl); err != nil {
					fmt.Println("Error: invalid TTL value")
					return
				}
				i++
			}
		}

		id, err := copyText(text, ttl, pasteLimit)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		if pasteLimit != nil {
			fmt.Printf("id: %s, ttl: %ds, paste limit: %d\n", id, ttl, *pasteLimit)
		} else {
			fmt.Printf("id: %s, ttl: %ds, paste limit: unlimited\n", id, ttl)
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

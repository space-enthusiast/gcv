package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
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

type FileMetadata struct {
	Filename    string `json:"filename"`
	SizeBytes   int64  `json:"sizeBytes"`
	ContentType string `json:"contentType"`
}

type CopyFilesRequest struct {
	Files      []FileMetadata `json:"files"`
	TTL        int            `json:"ttl"`
	PasteLimit *int           `json:"pasteLimit,omitempty"`
}

type PresignedUpload struct {
	Filename  string            `json:"filename"`
	ObjectKey string            `json:"objectKey"`
	PutURL    string            `json:"putUrl"`
	Headers   map[string]string `json:"headers"`
}

type CopyFilesResponse struct {
	ID      string            `json:"id"`
	Uploads []PresignedUpload `json:"uploads"`
}

type PresignedDownload struct {
	Filename  string            `json:"filename"`
	SizeBytes int64             `json:"sizeBytes"`
	GetURL    string            `json:"getUrl"`
	Headers   map[string]string `json:"headers"`
}

type PasteFilesResponse struct {
	Files []PresignedDownload `json:"files"`
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

func copyFiles(paths []string, ttl int, pasteLimit *int) (string, error) {
	if len(paths) == 0 {
		return "", errors.New("at least one file path required")
	}
	if ttl <= 0 || ttl > 600 {
		return "", errors.New("invalid ttl: must be in 1..600")
	}
	if pasteLimit != nil && *pasteLimit <= 0 {
		return "", errors.New("invalid paste limit: must be > 0")
	}

	metadata := make([]FileMetadata, 0, len(paths))
	for _, p := range paths {
		info, err := os.Stat(p)
		if err != nil {
			return "", fmt.Errorf("stat %s: %w", p, err)
		}
		if info.IsDir() {
			return "", fmt.Errorf("%s is a directory; only files are supported", p)
		}
		metadata = append(metadata, FileMetadata{
			Filename:    filepath.Base(p),
			SizeBytes:   info.Size(),
			ContentType: "application/octet-stream",
		})
	}

	body, _ := json.Marshal(CopyFilesRequest{Files: metadata, TTL: ttl, PasteLimit: pasteLimit})
	resp, err := http.Post(serverURL+"/copy/files", "application/json", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return "", fmt.Errorf("server rejected copy: %d %s", resp.StatusCode, string(b))
	}

	var registered CopyFilesResponse
	if err := json.NewDecoder(resp.Body).Decode(&registered); err != nil {
		return "", fmt.Errorf("decode copy response: %w", err)
	}
	if len(registered.Uploads) != len(paths) {
		return "", fmt.Errorf("server returned %d upload slots for %d files", len(registered.Uploads), len(paths))
	}

	for i, upload := range registered.Uploads {
		if err := putFile(paths[i], upload); err != nil {
			return "", fmt.Errorf("upload %s: %w", upload.Filename, err)
		}
	}

	return registered.ID, nil
}

func putFile(path string, upload PresignedUpload) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()

	info, err := f.Stat()
	if err != nil {
		return err
	}

	req, err := http.NewRequest(http.MethodPut, upload.PutURL, f)
	if err != nil {
		return err
	}
	req.ContentLength = info.Size()
	for k, v := range upload.Headers {
		req.Header.Set(k, v)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("storage rejected PUT: %d %s", resp.StatusCode, string(b))
	}
	return nil
}

func pasteFiles(id string, outDir string) ([]string, error) {
	if outDir == "" {
		outDir = "."
	}
	if err := os.MkdirAll(outDir, 0o755); err != nil {
		return nil, err
	}

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
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("server returned %d", resp.StatusCode)
	}

	var pasted PasteFilesResponse
	if err := json.NewDecoder(resp.Body).Decode(&pasted); err != nil {
		return nil, fmt.Errorf("decode paste response: %w", err)
	}
	if len(pasted.Files) == 0 {
		return nil, errors.New("no files in paste response (was this id a text paste?)")
	}

	written := make([]string, 0, len(pasted.Files))
	for _, d := range pasted.Files {
		safeName := filepath.Base(d.Filename)
		if safeName != d.Filename || safeName == "." || safeName == ".." || strings.ContainsAny(safeName, `/\`) {
			return written, fmt.Errorf("server returned unsafe filename %q", d.Filename)
		}
		dst := filepath.Join(outDir, safeName)
		if err := downloadFile(d, dst); err != nil {
			return written, fmt.Errorf("download %s: %w", safeName, err)
		}
		written = append(written, dst)
	}
	return written, nil
}

func downloadFile(d PresignedDownload, dst string) error {
	req, err := http.NewRequest(http.MethodGet, d.GetURL, nil)
	if err != nil {
		return err
	}
	for k, v := range d.Headers {
		req.Header.Set(k, v)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("storage rejected GET: %d %s", resp.StatusCode, string(b))
	}

	f, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer f.Close()
	if _, err := io.Copy(f, resp.Body); err != nil {
		return err
	}
	return nil
}

func parseFlag(arg, prefix string) (string, bool) {
	if strings.HasPrefix(arg, prefix) {
		return strings.TrimPrefix(arg, prefix), true
	}
	return "", false
}

func runCopyFiles(args []string) error {
	ttl := 600
	var pasteLimit *int
	paths := make([]string, 0, len(args))
	for _, a := range args {
		if v, ok := parseFlag(a, "--ttl="); ok {
			n, err := strconv.Atoi(v)
			if err != nil {
				return fmt.Errorf("invalid --ttl: %w", err)
			}
			ttl = n
			continue
		}
		if v, ok := parseFlag(a, "--limit="); ok {
			n, err := strconv.Atoi(v)
			if err != nil {
				return fmt.Errorf("invalid --limit: %w", err)
			}
			pasteLimit = &n
			continue
		}
		paths = append(paths, a)
	}

	id, err := copyFiles(paths, ttl, pasteLimit)
	if err != nil {
		return err
	}
	if pasteLimit != nil {
		fmt.Printf("id: %s, files: %d, ttl: %ds, pasteLimit: %d\n", id, len(paths), ttl, *pasteLimit)
	} else {
		fmt.Printf("id: %s, files: %d, ttl: %ds, pasteLimit: unlimited\n", id, len(paths), ttl)
	}
	return nil
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

func usage() {
	fmt.Println(`Usage:
  gcv -c "text" [TTL] [PASTE_LIMIT]    copy text
  gcv -v ID                            paste text
  gcv -qr ID                           print QR for ID
  gcv -cf PATH... [--ttl=N] [--limit=N] copy one or more files
  gcv -vf ID [OUT_DIR]                 paste files (default OUT_DIR=.)`)
}

func main() {
	if len(os.Args) < 2 {
		usage()
		return
	}

	cmd := os.Args[1]
	switch cmd {
	case "-c":
		if len(os.Args) < 3 {
			fmt.Println("Error: text argument required")
			return
		}
		text := os.Args[2]
		ttl := 600
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
	case "-v":
		if len(os.Args) != 3 {
			usage()
			return
		}
		id := os.Args[2]
		text, err := pasteText(id)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		fmt.Println(text.Text)
	case "-qr":
		if len(os.Args) != 3 {
			usage()
			return
		}
		id := os.Args[2]
		text, err := pasteText(id)
		if err != nil {
			fmt.Println("Error:", err)
			return
		}
		fmt.Println(text.Qr)
	case "-cf":
		if len(os.Args) < 3 {
			fmt.Println("Error: at least one file path required")
			return
		}
		if err := runCopyFiles(os.Args[2:]); err != nil {
			fmt.Println("Error:", err)
			os.Exit(1)
		}
	case "-vf":
		if len(os.Args) < 3 || len(os.Args) > 4 {
			usage()
			return
		}
		id := os.Args[2]
		outDir := ""
		if len(os.Args) == 4 {
			outDir = os.Args[3]
		}
		written, err := pasteFiles(id, outDir)
		if err != nil {
			fmt.Println("Error:", err)
			os.Exit(1)
		}
		for _, w := range written {
			fmt.Println(w)
		}
	default:
		usage()
	}
}

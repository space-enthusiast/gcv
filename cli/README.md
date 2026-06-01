# How to run

1, install go
2, run `go run cli.go` in the cli root directory


## Example
```
go run cli.go -c "{given string to copy}"
go run cli.go -v "{ID}"
go run cli.go -cf path/to/a.txt path/to/b.png [--ttl=600] [--limit=1]
go run cli.go -vf "{ID}" [out_dir]
```

`-cf` registers a bundle of files with the server, then PUTs each file
directly to the SeaweedFS endpoint using the presigned URL + SSE-C
headers the server returned. `-vf` does the reverse: fetches presigned
GET URLs from the server and downloads each file (with the SSE-C
headers replayed) into `out_dir` (default `.`).

## How to build

### Compile for Windows:
`GOOS=windows GOARCH=amd64 go build -o cli_window.exe cli.go`

### Compile for macOS:
`GOOS=darwin GOARCH=amd64 go build -o cli cli_mac.go`

### Compile for Linux:
`GOOS=linux GOARCH=amd64 go build -o cli cli_linux.go`

### Supporting ARM Architectures (Optional)

### If you want to support ARM (e.g., M1/M2 Macs or Raspberry Pi):
`GOOS=darwin GOARCH=arm64 go build -o cli_mac_arm cli.go`

### Compile for Linux ARM:
`GOOS=linux GOARCH=arm64 go build -o cli_linux_arm cli.go`
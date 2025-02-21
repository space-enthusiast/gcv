# How to run

1, install go
2, run `go run cli.go` in the cli root directory


## Example
```
go run -c "{given string to copy}"
go run -v "{ID}
```

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
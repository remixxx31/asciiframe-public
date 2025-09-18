# Security Policy

## Supported Versions

We actively support the following versions of AsciiFrame:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

If you discover a security vulnerability within AsciiFrame, please send an email to [your-email@domain.com]. All security vulnerabilities will be promptly addressed.

**Please do not report security vulnerabilities through public GitHub issues.**

### What to include in your report:

- Description of the vulnerability
- Steps to reproduce the issue
- Potential impact
- Any suggested fixes (optional)

### Response timeline:

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 1 week
- **Fix timeline**: Depends on severity (critical issues within 72 hours)

## Security Best Practices

When using AsciiFrame:

1. **Run in containers**: Use Docker for isolation
2. **Network security**: Don't expose directly to internet without proper firewall
3. **Input validation**: Be cautious with user-provided AsciiDoc content
4. **Updates**: Keep AsciiFrame updated to latest version
5. **Configuration**: Review `config.yml` security settings

## Known Security Considerations

- AsciiFrame processes user-provided AsciiDoc documents
- File system access is restricted to configured include paths
- Safe mode is enabled by default in server configuration
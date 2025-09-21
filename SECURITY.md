# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |

## Reporting a Vulnerability

Rex takes security seriously. If you discover a security vulnerability, please follow responsible disclosure:

### Contact

- **GitHub Issues**: For non-security bugs only
- **Security Issues**: Report privately via GitHub Security tab

### What to Include

1. **Description**: Clear explanation of the vulnerability
2. **Impact**: Potential security implications
3. **Reproduction**: Steps to reproduce the issue
4. **Environment**: Device, OS version, app version

### Response Timeline

- **Acknowledgment**: Within 48 hours
- **Assessment**: Within 1 week
- **Fix**: Depends on severity (critical issues prioritized)

## Security Features

### Encryption
- Private keys encrypted with AES-GCM using Android Keystore
- Non-exportable KEK with StrongBox when available
- Random DEK generation with secure zeroization

### Host Key Verification
- TOFU (Trust On First Use) model
- SHA256 fingerprint pinning
- Strict host key checking by default

### Data Protection
- Metadata-only logging with automatic redaction
- No sensitive data persistence in logs
- FLAG_SECURE on key and session screens

### Session Security
- Device credential unlock required
- Configurable session timeout (1-30 minutes)
- Optional clipboard with 60-second auto-clear

## Known Limitations

- Ed25519 key generation not yet implemented (placeholder)
- Full SSH key parsing implementation pending
- Device credential integration requires Android implementation

## Security Defaults

- Screenshots disabled on sensitive screens
- Clipboard copying disabled by default
- Host key verification enabled
- Cleartext traffic disabled
- App backup disabled

---

*For general questions about Rex, please use GitHub Issues.*
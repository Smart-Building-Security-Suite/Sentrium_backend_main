# Security Error Handling - Audit & Sanitization

## Overview
This document outlines the security measures implemented to prevent information disclosure through error messages.

## Security Vulnerabilities Fixed

### 1. User Enumeration Prevention ✅

**Issue**: Error messages that reveal whether a user account exists or not.

**Fixed Locations**:
- `PhoneAlreadyRegisteredException.java` - Removed phone number from error message
- `AuthService.requestPasswordResetOtp()` - Returns fake success for non-existent accounts
- `AuthService.verifyPasswordResetOtp()` - Returns generic "Invalid or expired OTP" instead of "No account found"

**Before**:
```java
throw new NotFoundException("No account found with this phone number");
throw new PhoneAlreadyRegisteredException("Phone number already registered: +2349012345678");
```

**After**:
```java
// Returns success even if account doesn't exist
return OtpRequestResponse.of(phoneNumber, Instant.now(), null);

// Generic error message
throw new PhoneAlreadyRegisteredException("This phone number is already registered. Please login instead.");
```

### 2. Attempt Count Information Disclosure ✅

**Issue**: Revealing remaining OTP attempts helps attackers know when to retry.

**Fixed**: Removed attempt count from error messages in both OTP verification flows.

**Before**:
```java
throw new OtpInvalidException("Invalid OTP. " + remaining + " attempt(s) remaining.");
```

**After**:
```java
throw new OtpInvalidException("Invalid OTP code");
```

### 3. Generic Error Message Sanitization ✅

**Issue**: Raw exception messages could leak internal details (IDs, paths, DB constraints).

**Fixed Locations**:
- `GlobalExceptionHandler.handleBadRequest()` - Returns generic "Invalid request"
- `GlobalExceptionHandler.handleNotFound()` - Returns generic "Resource not found"

**Before**:
```java
return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
```

**After**:
```java
log.info("Not found on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
return build(HttpStatus.NOT_FOUND, "Resource not found", request.getRequestURI());
```

### 4. Spring Boot Error Details Suppression ✅

**Added to `application.yml`**:
```yaml
server:
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false
```

## Secure Error Handling Patterns

### ✅ Good Practices Implemented

1. **Generic Authentication Errors**
   - Login failures: "Invalid credentials" (doesn't reveal if phone exists)
   - Token errors: "Invalid refresh token" (no token details exposed)

2. **Server-Side Logging Only**
   - Full exception details logged server-side
   - Only sanitized messages sent to client
   - No `printStackTrace()` anywhere in code

3. **Rate Limiting**
   - Generic "OTP already sent recently" message
   - Doesn't reveal timing details

4. **Consistent Error Format**
   - All errors use `ApiError` structure
   - Timestamp, status, generic error, sanitized message, path

### ⚠️ Warnings & Temporary Issues

#### OTP in Response (Development Only)
**File**: `OtpRequestResponse.java`

```java
public record OtpRequestResponse(String phoneNumber, Instant otpSentAt, int expiresIn, String otp)
```

**Status**: ⚠️ TEMPORARY - For development/testing only

**Action Required**: When SMS service is integrated:
1. Remove `otp` field from response
2. Make field nullable: `String otp` → handle null
3. Only send OTP via SMS, never in API response
4. Update tests to remove OTP from response assertions

## Error Message Guidelines for New Code

### ✅ DO
- Use generic messages: "Invalid request", "Resource not found", "Access denied"
- Log full details server-side with context
- Return consistent error structure via `ApiError`
- Use same error for similar scenarios (e.g., "Invalid credentials" for wrong password AND non-existent user)

### ❌ DON'T
- Include user input in error messages (IDs, emails, phone numbers)
- Reveal database constraint names or column names
- Expose stack traces or exception types
- Differentiate errors that could aid enumeration
- Include timing information or attempt counts

## Testing Error Messages

### Security Test Checklist

- [ ] Non-existent account returns same error as wrong password
- [ ] Password reset for non-existent phone returns success (timing-safe)
- [ ] OTP errors don't reveal remaining attempts
- [ ] 404 errors don't reveal entity IDs or internal paths
- [ ] Stack traces never appear in API responses
- [ ] Database constraint violations return generic errors

### Example Test Cases

```bash
# Should return "Invalid credentials" (not "User not found")
curl -X POST /api/v1/auth/login -d '{"phoneNumber":"9999999999","password":"test"}'

# Should return 200 (not 404) for non-existent phone
curl -X POST /api/v1/auth/password/reset/request -d '{"phoneNumber":"9999999999"}'

# Should return generic error (not "User with ID 123 not found")
curl -X GET /api/v1/users/non-existent-id -H "Authorization: Bearer $TOKEN"
```

## Compliance & Best Practices

### OWASP Top 10 Coverage
- ✅ A01:2021 - Broken Access Control (generic errors prevent enumeration)
- ✅ A04:2021 - Insecure Design (consistent error handling strategy)
- ✅ A05:2021 - Security Misconfiguration (error details suppressed)

### Standards Alignment
- ✅ CWE-209: Generation of Error Message Containing Sensitive Information
- ✅ CWE-203: Observable Discrepancy (timing-safe error responses)

## Monitoring & Alerts

### Recommended Alerts
1. High volume of 404 errors (potential enumeration attack)
2. Repeated OTP failures for same phone (brute force attempt)
3. Unusual error patterns (potential exploitation attempts)

### Log Monitoring
All security-relevant events are logged:
- Failed authentication attempts
- Non-existent account password reset requests
- Invalid OTP verification attempts
- Access denied events

---

**Last Updated**: 2026-08-06  
**Review Frequency**: Quarterly or after security incidents

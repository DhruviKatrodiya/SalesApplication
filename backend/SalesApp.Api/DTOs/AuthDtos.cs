namespace SalesApp.Api.DTOs;

public record LoginRequest(string Email, string Password);
public record LoginResponse(string Token, UserDto User);

public record UserDto(int Id, string FullName, string Email, string? Phone, string? ProfileImagePath);

public record ForgotPasswordRequest(string Email);
public record VerifyOtpRequest(string Email, string Otp);
public record ResetPasswordRequest(string Email, string Otp, string NewPassword);

// Email is editable from the profile screen.
public record UpdateProfileRequest(string FullName, string Email, string? Phone, string? ProfileImagePath);
public record ChangePasswordRequest(string CurrentPassword, string NewPassword);
public record MessageResponse(string Message);

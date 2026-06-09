using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SalesApp.Api.Data;
using SalesApp.Api.DTOs;
using SalesApp.Api.Services;

namespace SalesApp.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly JwtTokenService _jwt;
    private readonly OtpService _otp;
    private readonly IEmailService _email;

    public AuthController(AppDbContext db, JwtTokenService jwt, OtpService otp, IEmailService email)
    {
        _db = db;
        _jwt = jwt;
        _otp = otp;
        _email = email;
    }

    [HttpPost("login")]
    public async Task<ActionResult<LoginResponse>> Login(LoginRequest req)
    {
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email == req.Email);
        if (user is null || !BCrypt.Net.BCrypt.Verify(req.Password, user.PasswordHash))
            return Unauthorized(new MessageResponse("Invalid email or password."));

        var token = _jwt.CreateToken(user);
        var dto = new UserDto(user.Id, user.FullName, user.Email, user.Phone, user.ProfileImagePath);
        return Ok(new LoginResponse(token, dto));
    }

    [HttpPost("forgot-password")]
    public async Task<ActionResult<MessageResponse>> ForgotPassword(ForgotPasswordRequest req)
    {
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email == req.Email);
        // Always return OK to avoid leaking which emails exist.
        if (user is not null)
        {
            var otp = await _otp.GenerateAsync(req.Email);
            await _email.SendOtpAsync(req.Email, otp);
        }
        return Ok(new MessageResponse("If the email exists, an OTP has been sent."));
    }

    [HttpPost("verify-otp")]
    public async Task<ActionResult<MessageResponse>> VerifyOtp(VerifyOtpRequest req)
    {
        var ok = await _otp.VerifyAsync(req.Email, req.Otp, consume: false);
        if (!ok) return BadRequest(new MessageResponse("Invalid or expired OTP."));
        return Ok(new MessageResponse("OTP verified."));
    }

    [HttpPost("reset-password")]
    public async Task<ActionResult<MessageResponse>> ResetPassword(ResetPasswordRequest req)
    {
        var ok = await _otp.VerifyAsync(req.Email, req.Otp, consume: true);
        if (!ok) return BadRequest(new MessageResponse("Invalid or expired OTP."));

        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email == req.Email);
        if (user is null) return BadRequest(new MessageResponse("User not found."));

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(req.NewPassword);
        await _db.SaveChangesAsync();
        return Ok(new MessageResponse("Password has been reset successfully."));
    }
}

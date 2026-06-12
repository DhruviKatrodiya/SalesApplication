# ---- Build stage ----
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Restore (cached unless the csproj changes)
COPY SalesApp.Api.csproj ./
RUN dotnet restore SalesApp.Api.csproj

# Build + publish
COPY . ./
RUN dotnet publish SalesApp.Api.csproj -c Release -o /app/publish /p:UseAppHost=false

# ---- Runtime stage ----
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app
COPY --from=build /app/publish ./

# Kestrel listens on 8080 inside the container
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080

ENTRYPOINT ["dotnet", "SalesApp.Api.dll"]

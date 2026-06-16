using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SalesApp.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddIsActive : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "Trucks",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "SubCategories",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "StockRequests",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "Routes",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "Orders",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "Items",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "Customers",
                type: "bit",
                nullable: false,
                defaultValue: true);

            migrationBuilder.AddColumn<bool>(
                name: "IsActive",
                table: "Categories",
                type: "bit",
                nullable: false,
                defaultValue: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "Trucks");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "SubCategories");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "StockRequests");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "Routes");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "Orders");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "Items");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "Customers");

            migrationBuilder.DropColumn(
                name: "IsActive",
                table: "Categories");
        }
    }
}

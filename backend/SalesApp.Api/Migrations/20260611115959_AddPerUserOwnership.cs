using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SalesApp.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddPerUserOwnership : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "UserId",
                table: "SubCategories",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "UserId",
                table: "Routes",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "UserId",
                table: "Items",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "UserId",
                table: "Dispatches",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "UserId",
                table: "Customers",
                type: "int",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<int>(
                name: "UserId",
                table: "Categories",
                type: "int",
                nullable: false,
                defaultValue: 0);

            // Assign all pre-existing shared data to the current (first) salesperson so it is
            // owned rather than orphaned. New salespersons start with their own empty workspace.
            migrationBuilder.Sql(@"
                DECLARE @uid INT = (SELECT MIN(Id) FROM Users);
                UPDATE Categories     SET UserId = @uid WHERE UserId = 0;
                UPDATE SubCategories  SET UserId = @uid WHERE UserId = 0;
                UPDATE Items          SET UserId = @uid WHERE UserId = 0;
                UPDATE Customers      SET UserId = @uid WHERE UserId = 0;
                UPDATE Routes         SET UserId = @uid WHERE UserId = 0;
                UPDATE Dispatches     SET UserId = @uid WHERE UserId = 0;
                UPDATE Orders         SET SalesmanId = @uid WHERE SalesmanId IS NULL;
                UPDATE StockRequests  SET SalesmanId = @uid WHERE SalesmanId IS NULL;
            ");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "UserId",
                table: "SubCategories");

            migrationBuilder.DropColumn(
                name: "UserId",
                table: "Routes");

            migrationBuilder.DropColumn(
                name: "UserId",
                table: "Items");

            migrationBuilder.DropColumn(
                name: "UserId",
                table: "Dispatches");

            migrationBuilder.DropColumn(
                name: "UserId",
                table: "Customers");

            migrationBuilder.DropColumn(
                name: "UserId",
                table: "Categories");
        }
    }
}

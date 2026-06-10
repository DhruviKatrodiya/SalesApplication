using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SalesApp.Api.Migrations
{
    /// <inheritdoc />
    public partial class DispatchDraftPerTruck : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_DispatchDrafts_UserId",
                table: "DispatchDrafts");

            migrationBuilder.AlterColumn<string>(
                name: "TruckLabel",
                table: "DispatchDrafts",
                type: "nvarchar(450)",
                nullable: true,
                oldClrType: typeof(string),
                oldType: "nvarchar(max)",
                oldNullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_DispatchDrafts_UserId_TruckLabel",
                table: "DispatchDrafts",
                columns: new[] { "UserId", "TruckLabel" },
                unique: true,
                filter: "[TruckLabel] IS NOT NULL");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_DispatchDrafts_UserId_TruckLabel",
                table: "DispatchDrafts");

            migrationBuilder.AlterColumn<string>(
                name: "TruckLabel",
                table: "DispatchDrafts",
                type: "nvarchar(max)",
                nullable: true,
                oldClrType: typeof(string),
                oldType: "nvarchar(450)",
                oldNullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_DispatchDrafts_UserId",
                table: "DispatchDrafts",
                column: "UserId",
                unique: true);
        }
    }
}

using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SalesApp.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddTruckStock : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "TruckId",
                table: "Orders",
                type: "int",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "Trucks",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    UserId = table.Column<int>(type: "int", nullable: false),
                    Name = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    CreatedAt = table.Column<DateTime>(type: "datetime2", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Trucks", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "TruckStocks",
                columns: table => new
                {
                    Id = table.Column<int>(type: "int", nullable: false)
                        .Annotation("SqlServer:Identity", "1, 1"),
                    TruckId = table.Column<int>(type: "int", nullable: false),
                    ItemId = table.Column<int>(type: "int", nullable: false),
                    Quantity = table.Column<int>(type: "int", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_TruckStocks", x => x.Id);
                    table.ForeignKey(
                        name: "FK_TruckStocks_Items_ItemId",
                        column: x => x.ItemId,
                        principalTable: "Items",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "FK_TruckStocks_Trucks_TruckId",
                        column: x => x.TruckId,
                        principalTable: "Trucks",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_Orders_TruckId",
                table: "Orders",
                column: "TruckId");

            migrationBuilder.CreateIndex(
                name: "IX_Trucks_UserId_Name",
                table: "Trucks",
                columns: new[] { "UserId", "Name" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_TruckStocks_ItemId",
                table: "TruckStocks",
                column: "ItemId");

            migrationBuilder.CreateIndex(
                name: "IX_TruckStocks_TruckId_ItemId",
                table: "TruckStocks",
                columns: new[] { "TruckId", "ItemId" },
                unique: true);

            migrationBuilder.AddForeignKey(
                name: "FK_Orders_Trucks_TruckId",
                table: "Orders",
                column: "TruckId",
                principalTable: "Trucks",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_Orders_Trucks_TruckId",
                table: "Orders");

            migrationBuilder.DropTable(
                name: "TruckStocks");

            migrationBuilder.DropTable(
                name: "Trucks");

            migrationBuilder.DropIndex(
                name: "IX_Orders_TruckId",
                table: "Orders");

            migrationBuilder.DropColumn(
                name: "TruckId",
                table: "Orders");
        }
    }
}

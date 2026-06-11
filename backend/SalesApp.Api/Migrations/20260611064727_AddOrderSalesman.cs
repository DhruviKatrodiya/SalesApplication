using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SalesApp.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddOrderSalesman : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "SalesmanId",
                table: "Orders",
                type: "int",
                nullable: true);

            // Assign all pre-existing orders to the current (sole) salesperson so they
            // appear under "My Orders". New orders are owned by their creator.
            migrationBuilder.Sql(
                "UPDATE Orders SET SalesmanId = (SELECT MIN(Id) FROM Users) WHERE SalesmanId IS NULL;");

            migrationBuilder.CreateIndex(
                name: "IX_Orders_SalesmanId",
                table: "Orders",
                column: "SalesmanId");

            migrationBuilder.AddForeignKey(
                name: "FK_Orders_Users_SalesmanId",
                table: "Orders",
                column: "SalesmanId",
                principalTable: "Users",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_Orders_Users_SalesmanId",
                table: "Orders");

            migrationBuilder.DropIndex(
                name: "IX_Orders_SalesmanId",
                table: "Orders");

            migrationBuilder.DropColumn(
                name: "SalesmanId",
                table: "Orders");
        }
    }
}

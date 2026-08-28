using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace GriffGym.Infrastructure.Persistence.Migrations
{
    /// <inheritdoc />
    public partial class AddGoogleSignIn : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "google_subject_id",
                table: "user",
                type: "character varying(255)",
                maxLength: 255,
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "ix_user_google_subject_id",
                table: "user",
                column: "google_subject_id",
                unique: true,
                filter: "google_subject_id IS NOT NULL");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "ix_user_google_subject_id",
                table: "user");

            migrationBuilder.DropColumn(
                name: "google_subject_id",
                table: "user");
        }
    }
}

using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace GriffGym.Infrastructure.Persistence.Migrations
{
    /// <inheritdoc />
    public partial class InitialSchema : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateSequence(
                name: "griffgym_sync_version");

            migrationBuilder.CreateTable(
                name: "user",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    email = table.Column<string>(type: "character varying(256)", maxLength: 256, nullable: false),
                    normalized_email = table.Column<string>(type: "character varying(256)", maxLength: 256, nullable: false),
                    password_hash = table.Column<string>(type: "character varying(512)", maxLength: 512, nullable: false),
                    security_stamp = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    version = table.Column<int>(type: "integer", nullable: false),
                    sync_version = table.Column<long>(type: "bigint", nullable: false),
                    created_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    deleted_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_user", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "exercise",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    name = table.Column<string>(type: "character varying(120)", maxLength: 120, nullable: false),
                    category = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    version = table.Column<int>(type: "integer", nullable: false),
                    sync_version = table.Column<long>(type: "bigint", nullable: false),
                    created_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    deleted_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_exercise", x => x.id);
                    table.ForeignKey(
                        name: "fk_exercise_user_user_id",
                        column: x => x.user_id,
                        principalTable: "user",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "reference_max",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    lift = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    value_kg = table.Column<decimal>(type: "numeric(7,2)", nullable: false),
                    version = table.Column<int>(type: "integer", nullable: false),
                    sync_version = table.Column<long>(type: "bigint", nullable: false),
                    created_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    deleted_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_reference_max", x => x.id);
                    table.ForeignKey(
                        name: "fk_reference_max_user_user_id",
                        column: x => x.user_id,
                        principalTable: "user",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "refresh_token",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    token_hash = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    device_id = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: true),
                    expires_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    revoked_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    revocation_reason = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    replaced_by_token_id = table.Column<Guid>(type: "uuid", nullable: true),
                    created_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_refresh_token", x => x.id);
                    table.ForeignKey(
                        name: "fk_refresh_token_user_user_id",
                        column: x => x.user_id,
                        principalTable: "user",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "training_cycle",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    cycle_number = table.Column<int>(type: "integer", nullable: false),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    squat_reference_max_kg = table.Column<decimal>(type: "numeric(7,2)", nullable: false),
                    bench_press_reference_max_kg = table.Column<decimal>(type: "numeric(7,2)", nullable: false),
                    deadlift_reference_max_kg = table.Column<decimal>(type: "numeric(7,2)", nullable: false),
                    started_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    completed_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    version = table.Column<int>(type: "integer", nullable: false),
                    sync_version = table.Column<long>(type: "bigint", nullable: false),
                    created_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    deleted_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_training_cycle", x => x.id);
                    table.ForeignKey(
                        name: "fk_training_cycle_user_user_id",
                        column: x => x.user_id,
                        principalTable: "user",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "training_program",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    training_cycle_id = table.Column<Guid>(type: "uuid", nullable: false),
                    name = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    current_workout_template_id = table.Column<Guid>(type: "uuid", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_training_program", x => x.id);
                    table.ForeignKey(
                        name: "fk_training_program_training_cycle_training_cycle_id",
                        column: x => x.training_cycle_id,
                        principalTable: "training_cycle",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "training_week",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    training_program_id = table.Column<Guid>(type: "uuid", nullable: false),
                    week_number = table.Column<int>(type: "integer", nullable: false),
                    label = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_training_week", x => x.id);
                    table.ForeignKey(
                        name: "fk_training_week_training_program_training_program_id",
                        column: x => x.training_program_id,
                        principalTable: "training_program",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "workout_template",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    training_week_id = table.Column<Guid>(type: "uuid", nullable: false),
                    day_number = table.Column<int>(type: "integer", nullable: false),
                    sequence_number = table.Column<int>(type: "integer", nullable: false),
                    title = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_workout_template", x => x.id);
                    table.ForeignKey(
                        name: "fk_workout_template_training_week_training_week_id",
                        column: x => x.training_week_id,
                        principalTable: "training_week",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "exercise_template",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    workout_template_id = table.Column<Guid>(type: "uuid", nullable: false),
                    exercise_id = table.Column<Guid>(type: "uuid", nullable: false),
                    exercise_name = table.Column<string>(type: "character varying(120)", maxLength: 120, nullable: false),
                    exercise_category = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    position = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_exercise_template", x => x.id);
                    table.ForeignKey(
                        name: "fk_exercise_template_exercise_exercise_id",
                        column: x => x.exercise_id,
                        principalTable: "exercise",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_exercise_template_workout_template_workout_template_id",
                        column: x => x.workout_template_id,
                        principalTable: "workout_template",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "workout_session",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    user_id = table.Column<Guid>(type: "uuid", nullable: false),
                    training_cycle_id = table.Column<Guid>(type: "uuid", nullable: true),
                    training_week_id = table.Column<Guid>(type: "uuid", nullable: true),
                    workout_template_id = table.Column<Guid>(type: "uuid", nullable: true),
                    week_number = table.Column<int>(type: "integer", nullable: false),
                    day_number = table.Column<int>(type: "integer", nullable: false),
                    title = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    is_deload = table.Column<bool>(type: "boolean", nullable: false),
                    status = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    performed_on = table.Column<DateOnly>(type: "date", nullable: false),
                    started_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    finished_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true),
                    total_volume_kg = table.Column<decimal>(type: "numeric(12,2)", nullable: true),
                    notes = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: true),
                    version = table.Column<int>(type: "integer", nullable: false),
                    sync_version = table.Column<long>(type: "bigint", nullable: false),
                    created_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    updated_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    deleted_at_utc = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_workout_session", x => x.id);
                    table.ForeignKey(
                        name: "fk_workout_session_training_cycle_training_cycle_id",
                        column: x => x.training_cycle_id,
                        principalTable: "training_cycle",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_workout_session_training_week_training_week_id",
                        column: x => x.training_week_id,
                        principalTable: "training_week",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_workout_session_user_user_id",
                        column: x => x.user_id,
                        principalTable: "user",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                    table.ForeignKey(
                        name: "fk_workout_session_workout_template_workout_template_id",
                        column: x => x.workout_template_id,
                        principalTable: "workout_template",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                });

            migrationBuilder.CreateTable(
                name: "planned_set",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    exercise_template_id = table.Column<Guid>(type: "uuid", nullable: false),
                    position = table.Column<int>(type: "integer", nullable: false),
                    weight_kg = table.Column<decimal>(type: "numeric(7,2)", nullable: true),
                    reps = table.Column<int>(type: "integer", nullable: true),
                    rpe_min = table.Column<decimal>(type: "numeric(3,1)", nullable: true),
                    rpe_max = table.Column<decimal>(type: "numeric(3,1)", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_planned_set", x => x.id);
                    table.ForeignKey(
                        name: "fk_planned_set_exercise_template_exercise_template_id",
                        column: x => x.exercise_template_id,
                        principalTable: "exercise_template",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "exercise_log",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    workout_session_id = table.Column<Guid>(type: "uuid", nullable: false),
                    exercise_id = table.Column<Guid>(type: "uuid", nullable: true),
                    exercise_name = table.Column<string>(type: "character varying(120)", maxLength: 120, nullable: false),
                    exercise_category = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    type = table.Column<string>(type: "character varying(32)", maxLength: 32, nullable: false),
                    position = table.Column<int>(type: "integer", nullable: false),
                    notes = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_exercise_log", x => x.id);
                    table.ForeignKey(
                        name: "fk_exercise_log_exercise_exercise_id",
                        column: x => x.exercise_id,
                        principalTable: "exercise",
                        principalColumn: "id",
                        onDelete: ReferentialAction.SetNull);
                    table.ForeignKey(
                        name: "fk_exercise_log_workout_session_workout_session_id",
                        column: x => x.workout_session_id,
                        principalTable: "workout_session",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "set_log",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    exercise_log_id = table.Column<Guid>(type: "uuid", nullable: false),
                    position = table.Column<int>(type: "integer", nullable: false),
                    planned_weight_kg = table.Column<decimal>(type: "numeric(7,2)", nullable: true),
                    planned_reps = table.Column<int>(type: "integer", nullable: true),
                    planned_rpe_min = table.Column<decimal>(type: "numeric(3,1)", nullable: true),
                    planned_rpe_max = table.Column<decimal>(type: "numeric(3,1)", nullable: true),
                    actual_weight_kg = table.Column<decimal>(type: "numeric(7,2)", nullable: true),
                    actual_reps = table.Column<int>(type: "integer", nullable: true),
                    actual_rpe = table.Column<decimal>(type: "numeric(3,1)", nullable: true),
                    completed = table.Column<bool>(type: "boolean", nullable: false),
                    notes = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_set_log", x => x.id);
                    table.ForeignKey(
                        name: "fk_set_log_exercise_log_exercise_log_id",
                        column: x => x.exercise_log_id,
                        principalTable: "exercise_log",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "ix_exercise_sync_version",
                table: "exercise",
                column: "sync_version");

            migrationBuilder.CreateIndex(
                name: "ix_exercise_user_id_name",
                table: "exercise",
                columns: new[] { "user_id", "name" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_exercise_log_exercise_id",
                table: "exercise_log",
                column: "exercise_id");

            migrationBuilder.CreateIndex(
                name: "ix_exercise_log_workout_session_id_position",
                table: "exercise_log",
                columns: new[] { "workout_session_id", "position" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_exercise_template_exercise_id",
                table: "exercise_template",
                column: "exercise_id");

            migrationBuilder.CreateIndex(
                name: "ix_exercise_template_workout_template_id_position",
                table: "exercise_template",
                columns: new[] { "workout_template_id", "position" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_planned_set_exercise_template_id_position",
                table: "planned_set",
                columns: new[] { "exercise_template_id", "position" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_reference_max_sync_version",
                table: "reference_max",
                column: "sync_version");

            migrationBuilder.CreateIndex(
                name: "ix_reference_max_user_id_lift",
                table: "reference_max",
                columns: new[] { "user_id", "lift" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_refresh_token_token_hash",
                table: "refresh_token",
                column: "token_hash",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_refresh_token_user_id_expires_at_utc",
                table: "refresh_token",
                columns: new[] { "user_id", "expires_at_utc" });

            migrationBuilder.CreateIndex(
                name: "ix_set_log_exercise_log_id_position",
                table: "set_log",
                columns: new[] { "exercise_log_id", "position" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_training_cycle_sync_version",
                table: "training_cycle",
                column: "sync_version");

            migrationBuilder.CreateIndex(
                name: "ix_training_cycle_user_id_cycle_number",
                table: "training_cycle",
                columns: new[] { "user_id", "cycle_number" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_training_cycle_user_id_status",
                table: "training_cycle",
                columns: new[] { "user_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_training_program_training_cycle_id",
                table: "training_program",
                column: "training_cycle_id",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_training_week_training_program_id_week_number",
                table: "training_week",
                columns: new[] { "training_program_id", "week_number" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_user_normalized_email",
                table: "user",
                column: "normalized_email",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_user_sync_version",
                table: "user",
                column: "sync_version");

            migrationBuilder.CreateIndex(
                name: "ix_workout_session_sync_version",
                table: "workout_session",
                column: "sync_version");

            migrationBuilder.CreateIndex(
                name: "ix_workout_session_training_cycle_id",
                table: "workout_session",
                column: "training_cycle_id");

            migrationBuilder.CreateIndex(
                name: "ix_workout_session_training_week_id",
                table: "workout_session",
                column: "training_week_id");

            migrationBuilder.CreateIndex(
                name: "ix_workout_session_user_id_performed_on",
                table: "workout_session",
                columns: new[] { "user_id", "performed_on" });

            migrationBuilder.CreateIndex(
                name: "ix_workout_session_user_id_status",
                table: "workout_session",
                columns: new[] { "user_id", "status" });

            migrationBuilder.CreateIndex(
                name: "ix_workout_session_user_id_training_cycle_id_week_number",
                table: "workout_session",
                columns: new[] { "user_id", "training_cycle_id", "week_number" });

            migrationBuilder.CreateIndex(
                name: "ix_workout_session_workout_template_id",
                table: "workout_session",
                column: "workout_template_id");

            migrationBuilder.CreateIndex(
                name: "ix_workout_template_training_week_id_day_number",
                table: "workout_template",
                columns: new[] { "training_week_id", "day_number" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "planned_set");

            migrationBuilder.DropTable(
                name: "reference_max");

            migrationBuilder.DropTable(
                name: "refresh_token");

            migrationBuilder.DropTable(
                name: "set_log");

            migrationBuilder.DropTable(
                name: "exercise_template");

            migrationBuilder.DropTable(
                name: "exercise_log");

            migrationBuilder.DropTable(
                name: "exercise");

            migrationBuilder.DropTable(
                name: "workout_session");

            migrationBuilder.DropTable(
                name: "workout_template");

            migrationBuilder.DropTable(
                name: "training_week");

            migrationBuilder.DropTable(
                name: "training_program");

            migrationBuilder.DropTable(
                name: "training_cycle");

            migrationBuilder.DropTable(
                name: "user");

            migrationBuilder.DropSequence(
                name: "griffgym_sync_version");
        }
    }
}

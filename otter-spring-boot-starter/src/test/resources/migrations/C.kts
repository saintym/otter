import io.github.goodgoodjm.otter.core.Migration

object : Migration() {
    override val comment = "Raw query test"

    override fun up() {
        rawQuery("INSERT INTO customers (name, email) VALUES ('ggm0', 'ggm0@test.com')")
        rawQuery("INSERT INTO customers (name, email) VALUES ('ggm1', 'ggm1@test.com')")
        rawQuery("INSERT INTO customers (name, email) VALUES ('ggm2', 'ggm2@test.com')")
        rawQuery("INSERT INTO customers (name, email) VALUES ('ggm3', 'ggm3@test.com')")
        rawQuery("INSERT INTO customers (name, email) VALUES ('ggm4', 'ggm4@test.com')")
    }

    override fun down() {
    }
}


package com.example.quickcovid;

import java.io.File;
import java.io.IOException;
import java.util.function.DoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import quicksilver.webapp.simpleui.HtmlPageBootstrap;
import quicksilver.webapp.simpleui.bootstrap4.charts.TSFigurePanel;
import quicksilver.webapp.simpleui.bootstrap4.components.BSCard;
import quicksilver.webapp.simpleui.bootstrap4.components.BSComponentContainer;
import quicksilver.webapp.simpleui.bootstrap4.components.BSHeading;
import quicksilver.webapp.simpleui.bootstrap4.components.BSNavItem;
import quicksilver.webapp.simpleui.bootstrap4.components.BSNavbar;
import quicksilver.webapp.simpleui.bootstrap4.components.BSPanel;
import tech.tablesaw.aggregate.AggregateFunctions;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.DateColumn;
import tech.tablesaw.api.DateTimeColumn;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.charts.ChartBuilder;
import tech.tablesaw.io.Source;
import tech.tablesaw.io.csv.CsvReader;

public class MainPage extends HtmlPageBootstrap {

    @Override
    protected BSNavbar createNavbar() {
        BSNavbar bar = super.createNavbar();

        bar.add(new BSNavItem("COVID", "/"));
        return bar;
    }

    @Override
    protected BSComponentContainer createContentPane() {
        BSPanel p = new BSPanel();

        p.add(new BSHeading("Charts", 1));

        {
            Table allData = getAllData();

            StringColumn continent = allData.stringColumn("Country/Region")
                    .map(MainPage::continentOf, n -> StringColumn.create("Continent"));

            allData = allData.addColumns(continent);

            IntColumn confirmed = allData.intColumn("Confirmed");
            IntColumn deaths = allData.intColumn("Deaths");
            IntColumn recovered = allData.intColumn("Recovered");
            IntColumn ongoing = IntColumn.create("Ongoing", confirmed.size());
            for (int i = 0; i < confirmed.size(); i++) {
                int d = deaths.isMissing(i) ? 0 : deaths.get(i);
                int r = recovered.isMissing(i) ? 0 : recovered.get(i);
                ongoing.set(i, confirmed.get(i) - d - r);
//                System.out.println(i + " -> " + ongoing.get(i));
            }
            allData.addColumns(ongoing);

            allData = allData.summarize("Ongoing", "Confirmed", AggregateFunctions.sum)
                    .by("Country/Region", "Last Update", "Continent");

            allData.column("Sum [Ongoing]").setName("Ongoing");
            allData.column("Sum [Confirmed]").setName("Confirmed");

            System.out.println(allData.toString());

            allData = allData.summarize("Ongoing", "Confirmed", AggregateFunctions.sum)
                    .by("Last Update", "Continent");
            allData.column("Sum [Ongoing]").setName("Ongoing");
            allData.column("Sum [Confirmed]").setName("Confirmed");
            System.out.println(allData.toString());

            final Table fallData = allData;

            allData.splitOn("Continent")
                    .asTableList()
                    .forEach((Table t) -> {
                        Table continentSorted = t.sortDescendingOn("Last Update");
                        DoubleFunction<Double> trendOngoing = trend(continentSorted, "Ongoing");
                        double x2 = continentSorted.rowCount() - 1;

                        for (int days = 1; days <= 14; days++) {
                            double x3 = x2 + days;

                            double nextOngoing = trendOngoing.apply(x3);

                            Row row = fallData.appendRow();
                            row.setString("Continent", continentSorted.getString(0, "Continent"));
                            row.setDate("Last Update", continentSorted.dateColumn("Last Update").get(0).plusDays(days));
                            if (nextOngoing >= 0) {
                                row.setDouble("Ongoing", nextOngoing);
                            }
                        }
                    });

            ChartBuilder chartBuilder = ChartBuilder.createBuilder()
                    .dataTable(allData)
                    .chartType(ChartBuilder.CHART_TYPE.TIMESERIES)
                    .columnsForViewColumns("Last Update")
                    .columnsForViewRows("Ongoing")
                    .columnForColor("Continent");

//            chartBuilder.getLayoutBuilder()
//                    .autosize(true)
//                    .height(500);
            p.add(new BSCard(new TSFigurePanel(chartBuilder.divName("Ongoing").build(), "Ongoing"),
                    "Ongoing"));

            ChartBuilder chartBuilder2 = ChartBuilder.createBuilder()
                    .dataTable(allData)
                    .chartType(ChartBuilder.CHART_TYPE.TIMESERIES)
                    .columnsForViewColumns("Last Update")
                    .columnsForViewRows("Confirmed")
                    .columnForColor("Continent");

            p.add(new BSCard(new TSFigurePanel(chartBuilder2.divName("Confirmed").build(), "Confirmed"),
                    "Confirmed"));

        }

        return p;
    }

    Table getAllData() {
        return Stream.of(new File("COVID-19/csse_covid_19_data/csse_covid_19_daily_reports").listFiles((File pathname) -> pathname.getName().endsWith(".csv")))
                .map(f -> {
                    try {
                        Table t = new CsvReader().read(new Source(f));
                        if (t.column("Last Update").type() != ColumnType.LOCAL_DATE_TIME) {
                            Logger.getLogger(MainPage.class.getName()).log(Level.WARNING, "Bad timestamp for " + f);
                            return null;
                        } else {
                            //remove time from date time
                            DateTimeColumn update = t.dateTimeColumn("Last Update");
                            DateColumn date = update.map(d -> d.toLocalDate(), DateColumn::create);
                            t.replaceColumn("Last Update", date);

                            return t;
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(MainPage.class.getName()).log(Level.SEVERE, null, ex);
                        return null;
                    }
                })
                .filter(t -> t != null)
                .reduce((t, u) -> t.append(u))
                .get();
    }

    static String continentOf(String name) {
        //keep separate
        if ("Mainland China".equals(name)) {
            return "China";
        }
        if ("Others".equals(name)) {
            return "Others";
        }
        if ("Japan".equals(name)) {
            return "Japan";
        }

        //ugly way to encode all the countries
        String europe = "/San Marino/North Ireland/Lithuania/Belarus/Iceland/Czech Republic/Netherlands/Italy/France/Germany/Spain/UK/Denmark/Finland/Ireland/Estonia/Monaco/Luxembourg/Croatia/Greece/Romania/Switzerland/Austria/Sweden/Belgium/North Macedonia/Norway/";
        if (europe.contains("/" + name + "/")) {
            return "Europe";
        }
        String nAmerica = "/US/Canada/Mexico/Dominican Republic/";
        if (nAmerica.contains("/" + name + "/")) {
            return "North America";
        }
        String sAmerica = "/Brazil/Ecuador/";
        if (sAmerica.contains("/" + name + "/")) {
            return "South America";
        }
        String asia = "/Qatar/Georgia/Azerbaijan/Macau/Sri Lanka/Kuwait/Nepal/Cambodia/South Korea/Singapore/Hong Kong/Iran/Iraq/Thailand/Bahrain/Taiwan/Kuwait/Malaysia/Vietnam/United Arab Emirates/Oman/India/Philippines/Israel/Lebanon/Pakistan/Russia/Afghanistan/";
        if (asia.contains("/" + name + "/")) {
            return "Asia";
        }

        String africa = "/Algeria/Egypt/Nigeria/Ivory Coast/";
        if (africa.contains("/" + name + "/")) {
            return "Africa";
        }

        String oceania = "/Australia/New Zealand/";
        if (oceania.contains("/" + name + "/")) {
            return "Australia/Oceania";
        }

        System.out.println("No continent for " + name);
        return "N/A";
    }

    private static DoubleFunction<Double> trend(Table t, String columnName) {
        double y2 = t.doubleColumn(columnName).get(0);
        double x2 = t.doubleColumn(columnName).size() - 1;

        double y1 = t.doubleColumn(columnName).get(1);
        double x1 = x2 - 1;

        double slope = (y1 - y2) / (x1 - x2);

        double offset = y2 - (slope * x2);

        return x -> offset + slope * x;
    }
}

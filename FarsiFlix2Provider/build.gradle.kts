version = 1


cloudstream {
    language = "fa"
    // All of these properties are optional, you can safely remove them

    description = "Farsi Flix #2"
    authors = listOf("NoMeatNo")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Live",
    )

    iconUrl = "https://raw.githubusercontent.com/NoMeatNo/per3/master/logos/Fplex.png"
}

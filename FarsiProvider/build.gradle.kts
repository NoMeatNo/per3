version = 1


cloudstream {
    language = "Fa"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Naser, likdev256")

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

    iconUrl = "https://www.google.com/s2/favicons?domain=ww2.ibomma.cx&sz=%size%"
}

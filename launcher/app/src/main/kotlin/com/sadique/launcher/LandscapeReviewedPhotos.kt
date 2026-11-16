package com.sadique.launcher

import com.sadique.launcher.LandscapeFeed.Scene

/** Static photos reviewed for subject and landscape framing. */
internal object LandscapeReviewedPhotos {
    /** Visually reviewed 16:9 source renditions of individual, student and family portraits. */
    fun portraits(): MutableList<Scene> {
        val result = ArrayList<Scene>()
        portrait(result, "1758272133786-ee98adcc6837", "Friends together", null)
        portrait(result, "1758687126864-96b61e1b3af0", "Family at home", null)
        portrait(result, "1573894998033-c0cef4ed722b", "School classroom", 0.5)
        portrait(result, "1779896412109-1b221c2b350b", "Friends outdoors", 0.53)
        portrait(result, "1772724317387-7e763804b637", "Friends on a sunny day", 0.48)
        portrait(result, "1682203533347-10b924b3c253", "Reading in the library", 0.5)
        portrait(result, "1741699427799-3fbb70fce948", "Library study time", 0.6)
        portrait(result, "1652666087939-7db7dd87217b", "Baby in the grass", 0.44)
        portrait(result, "1753803806122-5166fbd4d0ed", "Baby in the garden", 0.48)
        portrait(result, "1761839258657-457dda39b5cc", "Family in the garden", 0.49)
        portrait(result, "1762368229295-81f2742fb8a5", "Sunset in Switzerland", 0.55)
        portrait(result, "1770564512661-eb2e142c26fd", "Women outdoors", 0.46)
        portrait(result, "1764740146693-4955d02c98f9", "Outdoor portrait · South Asia", 0.55)
        portrait(result, "1536010447069-d2c8af80c584", "Friends laughing together", null)
        portrait(result, "1556159916-26bf2ce06da9", "Friends in the tulip fields", null)
        portrait(result, "1758272134073-39aced2153e6", "Group selfie outdoors", null)
        // Reviewed Full HD crops for school, college, university and family scenes.
        portrait(result, "1553013476-7b48a900b429", "Schoolgirls in class", 0.5)
        portrait(result, "1762512346988-045f4d5ad2b3", "College study group", 0.5)
        portrait(result, "1741699428220-65f37f3fbbcb", "University library study", 0.5)
        portrait(result, "1758874960859-77d33502cbf4", "Three generations baking together", 0.5)
        /** Public-domain/CC0 people photos, with each 16:9 framing visually reviewed. */
        // flickr.com/photos/wendelinjacober/29941887810
        flickrPortrait(result, "5684", "29941887810_b3316581e3", "City balcony · Wendelin Jacober", 0)
        // flickr.com/photos/106274066@N07/54275237479
        flickrPortrait(result, "65535", "54275237479_6194e5949e", "Friends in the grass · snelson951", 50)
        // flickr.com/photos/wwward0/51676150106
        flickrPortrait(result, "65535", "51676150106_73b559de61", "Painters in East River Park · wwward0", 50)
        // flickr.com/photos/mussikatz/55595101640
        flickrPortrait(result, "65535", "55595101640_6d5cc4db13", "Weekend market in Bangkok · mussikatz", 35)
        // flickr.com/photos/mussikatz/52769851516
        flickrPortrait(result, "65535", "52769851516_86d01c2b9c", "Village life in Colombia · mussikatz", 35)
        return result
    }

    private fun portrait(list: MutableList<Scene>, id: String, subject: String, focusY: Double?) {
        val framing = if (focusY == null) "fit=max" else "fit=crop&crop=focalpoint&fp-x=0.5&fp-y=$focusY"
        list.add(
            Scene(
                "unsplash:$id", "https://images.unsplash.com/photo-$id?$framing" +
                    "&w=1920&h=1080&fm=jpg&q=78", subject, true
            )
        )
    }

    private fun flickrPortrait(list: MutableList<Scene>, server: String, file: String, subject: String, cropOffsetPercent: Int) {
        val id = file.substring(0, file.indexOf('_'))
        list.add(Scene("flickr:$id", "https://live.staticflickr.com/$server/${file}_b.jpg", subject, true, cropOffsetPercent))
    }

    fun oceanPhotos(): MutableList<Scene> {
        val result = ArrayList<Scene>()
        ocean(result, "warty-octopus", "2023/05/ex2103-octopod-1920x1080-1.jpg", "Warty octopus")
        ocean(result, "glass-sponge-wallpaper", "2026/01/ex1606-sponge-1920x1080-1.jpg", "Deep-sea glass sponge")
        ocean(result, "fishy-outcrop", "2022/09/ex2206-outcrop-1920x1080-1.jpg", "Rocky reef")
        ocean(result, "anemones-2", "2023/10/EX2306-anemones-1920x1080-1.jpg", "Deep-sea anemones")
        ocean(result, "coral", "2024/12/ex1606-coral-1920x1080-1.jpg", "Deep-sea coral")
        ocean(result, "chimaera-wallpaper", "2026/01/ex1907-chimaera-1920x1080-1.jpg", "Chimaera")
        return result
    }

    private fun ocean(list: MutableList<Scene>, id: String, path: String, subject: String) {
        list.add(
            Scene(
                "noaa:$id", "https://oceanexplorer.noaa.gov/wp-content/uploads/$path",
                "$subject · NOAA Ocean Exploration", true
            )
        )
    }

    /** Selected public domain and CC0 Full HD downloads. */
    fun wallpapersPhotos(): MutableList<Scene> {
        val photos = arrayOf(
            arrayOf("6a69283189", "Grand Canyon sandstone"),
            arrayOf("cbeb40bf8c", "Yellowstone geyser basin"),
            arrayOf("24b32f771f", "Autumn hills above a blue lake"),
            arrayOf("c4b06d8c7e", "Snow-covered bog and forest"),
            arrayOf("0e97187bef", "Grand Canyon from the Rim Trail"),
            arrayOf("9a39052f96", "Trail through a tundra valley"),
            arrayOf("85e372d5ff", "Spruce forest and moss-covered rocks"),
            arrayOf("92cb432c51", "Aurora above Lofoten Bay"),
            arrayOf("680f4bba3b", "Terrace Mountain")
        )
        val result = ArrayList<Scene>()
        for (photo in photos) {
            result.add(
                Scene(
                    "wallpapers:" + photo[0], "https://wallpapers.org/download/" + photo[0] + "/fhd",
                    photo[1], true
                )
            )
        }
        return result
    }

    fun publicDomainPicturesPhotos(): MutableList<Scene> {
        val photos = arrayOf(
            arrayOf("200000/velka/fall-by-the-lake-14767797082J2.jpg", "Autumn by the lake"),
            arrayOf("470000/velka/landschap-bergweg-haarspeldbocht-1665124155qIR.jpg", "Winding mountain road"),
            arrayOf("240000/velka/forest-landscape-1509002460MBr.jpg", "Sunlight in a birch forest")
        )
        val result = ArrayList<Scene>()
        for (photo in photos) {
            result.add(
                Scene(
                    "publicdomainpictures:" + photo[0],
                    "https://www.publicdomainpictures.net/pictures/" + photo[0], photo[1], true
                )
            )
        }
        return result
    }

    fun nationalParkPhotos(): MutableList<Scene> {
        // Public domain. Photographer: National Trails Office, U.S. National Park Service.
        val id = "29fc4bc2-b103-4b1b-939f-19d4b91ce209"
        return mutableListOf(
            Scene(
                "nps:$id",
                "https://npgallery.nps.gov/GetAsset/$id/original.jpg",
                "Mississippi River · U.S. National Park Service", true
            )
        )
    }

    fun pxherePhotos(): MutableList<Scene> {
        return mutableListOf(
            Scene(
                "pxhere:668634",
                "https://c.pxhere.com/photos/00/95/lake_landscape_reflection_scenic_calm_outdoors_nature_trees-668634.jpg!d",
                "Reflections on a mountain lake", true
            )
        )
    }
}

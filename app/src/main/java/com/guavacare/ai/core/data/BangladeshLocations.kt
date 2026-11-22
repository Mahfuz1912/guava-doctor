package com.guavacare.ai.core.data

import com.guavacare.ai.domain.model.AppLanguage

data class LocationOption(
    val nameEn: String,
    val nameBn: String
) {
    fun localized(language: AppLanguage): String = when (language) {
        AppLanguage.ENGLISH -> nameEn
        AppLanguage.BANGLA -> nameBn
    }

    fun bilingualLabel(language: AppLanguage): String = when (language) {
        AppLanguage.BANGLA -> "$nameBn ($nameEn)"
        AppLanguage.ENGLISH -> "$nameEn ($nameBn)"
    }
}

/**
 * Complete cascading administrative directory of Bangladesh:
 * 8 Divisions -> 64 Districts -> Upazilas / Thanas (Bilingual Bangla & English).
 */
object BangladeshLocations {

    val divisions: List<LocationOption> = listOf(
        LocationOption("Dhaka", "ঢাকা"),
        LocationOption("Chattogram", "চট্টগ্রাম"),
        LocationOption("Rajshahi", "রাজশাহী"),
        LocationOption("Khulna", "খুলনা"),
        LocationOption("Barishal", "বরিশাল"),
        LocationOption("Sylhet", "সিলেট"),
        LocationOption("Rangpur", "রংপুর"),
        LocationOption("Mymensingh", "ময়মনসিংহ")
    )

    private val districtsByDivision: Map<String, List<LocationOption>> = mapOf(
        "Dhaka" to listOf(
            LocationOption("Dhaka", "ঢাকা"),
            LocationOption("Faridpur", "ফরিদপুর"),
            LocationOption("Gazipur", "গাজীপুর"),
            LocationOption("Gopalganj", "গোপালগঞ্জ"),
            LocationOption("Kishoreganj", "কিশোরগঞ্জ"),
            LocationOption("Madaripur", "মাদারীপুর"),
            LocationOption("Manikganj", "মানিকগঞ্জ"),
            LocationOption("Munshiganj", "মুন্সীগঞ্জ"),
            LocationOption("Narayanganj", "নারায়ণগঞ্জ"),
            LocationOption("Narsingdi", "নরসিংদী"),
            LocationOption("Rajbari", "রাজবাড়ী"),
            LocationOption("Shariatpur", "শরীয়তপুর"),
            LocationOption("Tangail", "টাঙ্গাইল")
        ),
        "Chattogram" to listOf(
            LocationOption("Bandarban", "বান্দরবান"),
            LocationOption("Brahmanbaria", "ব্রাহ্মণবাড়িয়া"),
            LocationOption("Chandpur", "চাঁদপুর"),
            LocationOption("Chattogram", "চট্টগ্রাম"),
            LocationOption("Comilla", "কুমিল্লা"),
            LocationOption("Cox's Bazar", "কক্সবাজার"),
            LocationOption("Feni", "ফেনী"),
            LocationOption("Khagrachhari", "খাগড়াছড়ি"),
            LocationOption("Lakshmipur", "লক্ষ্মীপুর"),
            LocationOption("Noakhali", "নোয়াখালী"),
            LocationOption("Rangamati", "রাঙ্গামাটি")
        ),
        "Rajshahi" to listOf(
            LocationOption("Bogura", "বগুড়া"),
            LocationOption("Chapainawabganj", "চাঁপাইনবাবগঞ্জ"),
            LocationOption("Joypurhat", "জয়পুরহাট"),
            LocationOption("Naogaon", "নওগাঁ"),
            LocationOption("Natore", "নাটোর"),
            LocationOption("Pabna", "পাবনা"),
            LocationOption("Rajshahi", "রাজশাহী"),
            LocationOption("Sirajganj", "সিরাজগঞ্জ")
        ),
        "Khulna" to listOf(
            LocationOption("Bagerhat", "বাগেরহাট"),
            LocationOption("Chuadanga", "চুয়াডাঙ্গা"),
            LocationOption("Jashore", "যশোর"),
            LocationOption("Jhenaidah", "ঝিনাইদহ"),
            LocationOption("Khulna", "খুলনা"),
            LocationOption("Kushtia", "কুষ্টিয়া"),
            LocationOption("Magura", "মাগুরা"),
            LocationOption("Meherpur", "মেহেরপুর"),
            LocationOption("Narail", "নড়াইল"),
            LocationOption("Satkhira", "সাতক্ষীরা")
        ),
        "Barishal" to listOf(
            LocationOption("Barguna", "বরগুনা"),
            LocationOption("Barishal", "বরিশাল"),
            LocationOption("Bhola", "ভোলা"),
            LocationOption("Jhalokati", "ঝালকাঠি"),
            LocationOption("Patuakhali", "পটুয়াখালী"),
            LocationOption("Pirojpur", "পিরোজপুর")
        ),
        "Sylhet" to listOf(
            LocationOption("Habiganj", "হবিগঞ্জ"),
            LocationOption("Moulvibazar", "মৌলভীবাজার"),
            LocationOption("Sunamganj", "সুনামগঞ্জ"),
            LocationOption("Sylhet", "সিলেট")
        ),
        "Rangpur" to listOf(
            LocationOption("Dinajpur", "দিনাজপুর"),
            LocationOption("Gaibandha", "গাইবান্ধা"),
            LocationOption("Kurigram", "কুড়িগ্রাম"),
            LocationOption("Lalmonirhat", "লালমনিরহাট"),
            LocationOption("Nilphamari", "নীলফামারী"),
            LocationOption("Panchagarh", "পঞ্চগড়"),
            LocationOption("Rangpur", "রংপুর"),
            LocationOption("Thakurgaon", "ঠাকুরগাঁও")
        ),
        "Mymensingh" to listOf(
            LocationOption("Jamalpur", "জামালপুর"),
            LocationOption("Mymensingh", "ময়মনসিংহ"),
            LocationOption("Netrokona", "নেত্রকোণা"),
            LocationOption("Sherpur", "শেরপুর")
        )
    )

    private fun parseUpazilas(raw: String): List<LocationOption> =
        raw.split("|").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size == 2) LocationOption(parts[0].trim(), parts[1].trim()) else null
        }

    private val upazilasByDistrict: Map<String, List<LocationOption>> = mapOf(
        // Dhaka Division
        "Dhaka" to parseUpazilas("Dhamrai:ধামরাই|Dohar:দোহার|Keraniganj:কেরানীগঞ্জ|Nawabganj:নবাবগঞ্জ|Savar:সাভার|Dhaka Metro:ঢাকা মেট্রো"),
        "Faridpur" to parseUpazilas("Alfadanga:আলফাডাঙ্গা|Bhanga:ভাঙ্গা|Boalmari:বোয়ালমারী|Charbhadrasan:চরভদ্রাসন|Faridpur Sadar:ফরিদপুর সদর|Madhukhali:মধুখালী|Nagarkanda:নগরকান্দা|Sadarpur:সদরপুর|Saltha:সালথা"),
        "Gazipur" to parseUpazilas("Gazipur Sadar:গাজীপুর সদর|Kaliakair:কালিয়াকৈর|Kaliganj:কালীগঞ্জ|Kapasia:কাপাসিয়া|Sreepur:শ্রীপুর"),
        "Gopalganj" to parseUpazilas("Gopalganj Sadar:গোপালগঞ্জ সদর|Kashiani:কাশিয়ানী|Kotalipara:কোটালীপাড়া|Muksudpur:মুকসুদপুর|Tungipara:টুঙ্গিপাড়া"),
        "Kishoreganj" to parseUpazilas("Austagram:অষ্টগ্রাম|Bajitpur:বাজিতপুর|Bhairab:ভৈরব|Hossainpur:হোসেনপুর|Itna:ইটনা|Karimganj:করিমগঞ্জ|Katiadi:কটিয়াদী|Kishoreganj Sadar:কিশোরগঞ্জ সদর|Kuliarchar:কুলিয়ারচর|Mithamain:মিঠামইন|Nikli:নিকলী|Pakundia:পাকুন্দিয়া|Tarail:তাড়াইল"),
        "Madaripur" to parseUpazilas("Kalkini:কালকিনি|Madaripur Sadar:মাদারীপুর সদর|Rajoir:রাজৈর|Shibchar:শিবচর|Dasar:ডাসার"),
        "Manikganj" to parseUpazilas("Daulatpur:দৌলতপুর|Ghior:ঘিওর|Harirampur:হরিরামপুর|Manikganj Sadar:মানিকগঞ্জ সদর|Saturia:সাটুরিয়া|Shivalaya:শিবালয়|Singair:সিংগাইর"),
        "Munshiganj" to parseUpazilas("Gazaria:গজারিয়া|Lohajang:লৌহজং|Munshiganj Sadar:মুন্সীগঞ্জ সদর|Sirajdikhan:সিরাজদিখান|Sreenagar:শ্রীনগর|Tongibari:টঙ্গীবাড়ী"),
        "Narayanganj" to parseUpazilas("Araihazar:আড়াইহাজার|Bandar:বন্দর|Narayanganj Sadar:নারায়ণগঞ্জ সদর|Rupganj:রূপগঞ্জ|Sonargaon:সোনারগাঁও"),
        "Narsingdi" to parseUpazilas("Belabo:বেলাবো|Monohardi:মনোহরদী|Narsingdi Sadar:নরসিংদী সদর|Palash:পলাশ|Raipura:রায়পুরা|Shibpur:শিবপুর"),
        "Rajbari" to parseUpazilas("Baliakandi:বালিয়াকান্দি|Goalandaghat:গোয়ালন্দ|Kalukhali:কালুখালী|Pangsha:পাংশা|Rajbari Sadar:রাজবাড়ী সদর"),
        "Shariatpur" to parseUpazilas("Bhedarganj:ভেদরগঞ্জ|Damudya:ডামুড্যা|Gosairhat:গোসাইরহাট|Naria:নড়িয়া|Shariatpur Sadar:শরীয়তপুর সদর|Zajira:জাজিরা"),
        "Tangail" to parseUpazilas("Basail:বাসাইল|Bhuapur:ভূঞাপুর|Delduar:দেলদুয়ার|Dhanbari:ধনবাড়ী|Ghatail:ঘাটাইল|Gopalpur:গোপালপুর|Kalihati:কালিহাতী|Madhupur:মধুপুর|Mirzapur:মির্জাপুর|Nagarpur:নাগরপুর|Sakhipur:সখিপুর|Tangail Sadar:টাঙ্গাইল সদর"),

        // Chattogram Division
        "Bandarban" to parseUpazilas("Alikadam:আলীকদম|Bandarban Sadar:বান্দরবান সদর|Lama:লামা|Naikhongchhari:নাইক্ষ্যংছড়ি|Rowangchhari:রোয়াংছড়ি|Ruma:রুমা|Thanchi:থানচি"),
        "Brahmanbaria" to parseUpazilas("Akhaura:আখাউড়া|Ashuganj:আশুগঞ্জ|Bancharampur:বাঞ্ছারামপুর|Bijoynagar:বিজয়নগর|Brahmanbaria Sadar:ব্রাহ্মণবাড়িয়া সদর|Kasba:কসবা|Nabinagar:নবীনগর|Nasirnagar:নাসিরনগর|Sarail:সরাইল"),
        "Chandpur" to parseUpazilas("Chandpur Sadar:চাঁদপুর সদর|Faridganj:ফরিদগঞ্জ|Haimchar:হাইমচর|Haziganj:হাজীগঞ্জ|Kachua:কচুয়া|Matlab Dakshin:মতলব দক্ষিণ|Matlab Uttar:মতলব উত্তর|Shahrasti:শাহরাস্তি"),
        "Chattogram" to parseUpazilas("Anwara:আনোয়ারা|Banshkhali:বাঁশখালী|Boalkhali:বোয়ালখালী|Chandanaish:চন্দনাইশ|Fatikchhari:ফটিকছড়ি|Hathazari:হাটহাজারী|Karnaphuli:কর্ণফুলী|Lohagara:লোহাগাড়া|Mirsharai:মীরসরাই|Patiya:পটিয়া|Rangunia:রাঙ্গুনিয়া|Raozan:রাউজান|Sandwip:সন্দ্বীপ|Satkania:সাতকানিয়া|Sitakunda:সীতাকুণ্ড|Chattogram Metro:চট্টগ্রাম মেট্রো"),
        "Comilla" to parseUpazilas("Barura:বরুড়া|Brahmanpara:ব্রাহ্মণপাড়া|Burichang:বুড়িচং|Chandina:চান্দিনা|Chauddagram:চৌদ্দগ্রাম|Comilla Adarsha Sadar:কুমিল্লা আদর্শ সদর|Comilla Sadar Dakshin:কুমিল্লা সদর দক্ষিণ|Daudkandi:দাউদকান্দি|Debidwar:দেবিদ্বার|Homna:হোমনা|Laksam:লাকসাম|Lalmai:লালমাই|Meghna:মেঘনা|Monohorgonj:মনোহরগঞ্জ|Muradnagar:মুরাদনগর|Nangalkot:নাঙ্গলকোট|Titas:তিতাস"),
        "Cox's Bazar" to parseUpazilas("Chakaria:চকরিয়া|Cox's Bazar Sadar:কক্সবাজার সদর|Eidgaon:ঈদগাঁও|Kutubdia:কুতুবদিয়া|Maheshkhali:মহেশখালী|Pekua:পেকুয়া|Ramu:রামু|Teknaf:টেকনাফ|Ukhia:উখিয়া"),
        "Feni" to parseUpazilas("Chhagalnaiya:ছাগলনাইয়া|Daganbhuiyan:দাগনভূঞা|Feni Sadar:ফেনী সদর|Fulgazi:ফুলগাজী|Parshuram:পরশুরাম|Sonagazi:সোনাগাজী"),
        "Khagrachhari" to parseUpazilas("Dighinala:দীঘিনালা|Guimara:গুইমারা|Khagrachhari Sadar:খাগড়াছড়ি সদর|Lakshmichhari:লক্ষ্মীছড়ি|Mahalchhari:মহালছড়ি|Manikchhari:মানিকছড়ি|Matiranga:মাটিরাঙ্গা|Panchhari:পানছড়ি|Ramgarh:রামগড়"),
        "Lakshmipur" to parseUpazilas("Kamalnagar:কমলনগর|Lakshmipur Sadar:লক্ষ্মীপুর সদর|Raipur:রায়পুর|Ramganj:রামগঞ্জ|Ramgati:রামগতি"),
        "Noakhali" to parseUpazilas("Begumganj:বেগমগঞ্জ|Chatkhil:চাটখিল|Companiganj:কোম্পানীগঞ্জ|Hatiya:হাতিয়া|Kabirhat:কবিরহাট|Noakhali Sadar:নোয়াখালী সদর|Senbagh:সেনবাগ|Sonaimuri:সোনাইমুড়ী|Subarnachar:সুবর্ণচর"),
        "Rangamati" to parseUpazilas("Baghaichhari:বাঘাইছড়ি|Barkal:বরকল|Belaichhari:বিলাইছড়ি|Juraichhari:জুরাছড়ি|Kaptai:কাপ্তাই|Kawkhali:কাউখালী|Langadu:লংগদু|Naniarchar:নানিয়ারচর|Rajasthali:রাজস্থলী|Rangamati Sadar:রাঙ্গামাটি সদর"),

        // Rajshahi Division
        "Bogura" to parseUpazilas("Adamdighi:আদমদীঘি|Bogura Sadar:বগুড়া সদর|Dhunat:ধুনট|Dhupchanchia:দুপচাঁচিয়া|Gabtali:গাবতলী|Kahaloo:কাহালু|Nandigram:নন্দীগ্রাম|Sariakandi:সারিয়াকান্দি|Shajahanpur:শাজাহানপুর|Sherpur:শেরপুর|Shibganj:শিবগঞ্জ|Sonatola:সোনাতলা"),
        "Chapainawabganj" to parseUpazilas("Bholahat:ভোলাহাট|Chapainawabganj Sadar:চাঁপাইনবাবগঞ্জ সদর|Gomastapur:গোমস্তাপুর|Nachole:নাচোল|Shibganj:শিবগঞ্জ"),
        "Joypurhat" to parseUpazilas("Akkelpur:আক্কেলপুর|Joypurhat Sadar:জয়পুরহাট সদর|Kalai:কালাই|Khetlal:ক্ষেতলাল|Panchbibi:পাঁচবিবি"),
        "Naogaon" to parseUpazilas("Atrai:আত্রাই|Badalgachhi:বদলগাছী|Dhamoirhat:ধামইরহাট|Manda:মান্দা|Mohadevpur:মহাদেবপুর|Naogaon Sadar:নওগাঁ সদর|Niamatpur:নিয়ামতপুর|Patnitala:পত্নীতলা|Porsha:পোরশা|Raninagar:রাণীনগর|Sapahar:সাপাহার"),
        "Natore" to parseUpazilas("Bagatipara:বাগাতিপাড়া|Baraigram:বড়াইগ্রাম|Gurudaspur:গুরুদাসপুর|Lalpur:লালপুর|Naldanga:নলডাঙ্গা|Natore Sadar:নাটোর সদর|Singra:সিংড়া"),
        "Pabna" to parseUpazilas("Atgharia:আটঘরিয়া|Bera:বেড়া|Bhangura:ভাঙ্গুড়া|Chatmohar:চাটমোহর|Faridpur:ফরিদপুর|Ishwardi:ঈশ্বরদী|Pabna Sadar:পাবনা সদর|Santhia:সাঁথিয়া|Sujanagar:সুজানগর"),
        "Rajshahi" to parseUpazilas("Bagha:বাঘা|Bagmara:বাগমারা|Charghat:চারঘাট|Durgapur:দুর্গাপুর|Godagari:গোদাগাড়ী|Mohanpur:মোহনপুর|Paba:পবা|Puthia:পুঠিয়া|Tanore:তানোর|Rajshahi Metro:রাজশাহী মেট্রো"),
        "Sirajganj" to parseUpazilas("Belkuchi:বেলকুচি|Chauhali:চৌহালী|Kamarkhanda:কামারখন্দ|Kazipur:কাজীপুর|Raiganj:রায়গঞ্জ|Shahjadpur:শাহজাদপুর|Sirajganj Sadar:সিরাজগঞ্জ সদর|Tarash:তাড়াশ|Ullahpara:উল্লাপাড়া"),

        // Khulna Division
        "Bagerhat" to parseUpazilas("Bagerhat Sadar:বাগেরহাট সদর|Chitalmari:চিতলমারী|Fakirhat:ফকিরহাট|Kachua:কচুয়া|Mollahat:মোল্লাহাট|Mongla:মোংলা|Morrelganj:মোড়েলগঞ্জ|Rampal:রামপাল|Sarankhola:শরণখোলা"),
        "Chuadanga" to parseUpazilas("Alamdanga:আলমডাঙ্গা|Chuadanga Sadar:চুয়াডাঙ্গা সদর|Damurhuda:দামুড়হুদা|Jibannagar:জীবননগর"),
        "Jashore" to parseUpazilas("Abhaynagar:অভয়নগর|Bagherpara:বাঘারপাড়া|Chaugachha:চৌগাছা|Jashore Sadar:যশোর সদর|Jhikargachha:ঝিকরগাছা|Keshabpur:কেশবপুর|Manirampur:মণিরামপুর|Sharsha:শার্শা"),
        "Jhenaidah" to parseUpazilas("Harinakunda:হরিণাকুণ্ডু|Jhenaidah Sadar:ঝিনাইদহ সদর|Kaliganj:কালীগঞ্জ|Kotchandpur:কোটচাঁদপুর|Maheshpur:মহেশপুর|Shailkupa:শৈলকুপা"),
        "Khulna" to parseUpazilas("Batiaghata:বটিয়াঘাটা|Dacope:দাকোপ|Dighalia:দিঘলিয়া|Dumuria:ডুমুরিয়া|Koyra:কয়রা|Paikgachha:পাইকগাছা|Phultala:ফুলতলা|Rupsha:রূপসা|Terokhada:তেরখাদা|Khulna Metro:খুলনা মেট্রো"),
        "Kushtia" to parseUpazilas("Bheramara:ভেড়ামারা|Daulatpur:দৌলতপুর|Khoksa:খোকসা|Kumarkhali:কুমারখালী|Kushtia Sadar:কুষ্টিয়া সদর|Mirpur:মিরপুর"),
        "Magura" to parseUpazilas("Magura Sadar:মাগুরা সদর|Mohammadpur:মহম্মদপুর|Shalikha:শালিখা|Sreepur:শ্রীপুর"),
        "Meherpur" to parseUpazilas("Gangni:গাংনী|Meherpur Sadar:মেহেরপুর সদর|Mujibnagar:মুজিবনগর"),
        "Narail" to parseUpazilas("Kalia:কালিয়া|Lohagara:লোহাগড়া|Narail Sadar:নড়াইল সদর"),
        "Satkhira" to parseUpazilas("Assasuni:আশাশুনি|Debhata:দেবহাটা|Kalaroa:কলারোয়া|Kaliganj:কালীগঞ্জ|Satkhira Sadar:সাতক্ষীরা সদর|Shyamnagar:শ্যামনগর|Tala:তালা"),

        // Barishal Division (Major Guava Belt!)
        "Barguna" to parseUpazilas("Amtali:আমতলী|Bamna:বামনা|Barguna Sadar:বরগুনা সদর|Betagi:বেতাগী|Patharghata:পাথরঘাটা|Taltali:তালতলী"),
        "Barishal" to parseUpazilas("Agailjhara:আগৈলঝাড়া|Babuganj:বাবুগঞ্জ|Bakerganj:বাকেরগঞ্জ|Banaripara:বানারীপাড়া|Barishal Sadar:বরিশাল সদর|Gaurnadi:গৌরনদী|Hizla:হিজলা|Mehendiganj:মেহেন্দিগঞ্জ|Muladi:মুলাদী|Wazirpur:উজিরপুর"),
        "Bhola" to parseUpazilas("Bhola Sadar:ভোলা সদর|Burhanuddin:বোরহানউদ্দিন|Char Fasson:চরফ্যাশন|Daulatkhan:দৌলতখান|Lalmohan:লালমোহন|Manpura:মনপুরা|Tazumuddin:তজুমদ্দিন"),
        "Jhalokati" to parseUpazilas("Jhalokati Sadar:ঝালকাঠি সদর|Kathalia:কাঁঠালিয়া|Nalchity:নলছিটি|Rajapur:রাজাপুর"),
        "Patuakhali" to parseUpazilas("Bauphal:বাউফল|Dashmina:দশমিনা|Dumki:দুমকী|Galachipa:গলাচিপা|Kalapara:কলাপাড়া|Mirzaganj:মির্জাগঞ্জ|Patuakhali Sadar:পটুয়াখালী সদর|Rangabali:রাঙ্গাবালী"),
        "Pirojpur" to parseUpazilas("Bhandaria:ভান্ডারিয়া|Indurkani:ইন্দুরকানী|Kawkhali:কাউখালী|Mathbaria:মঠবাড়িয়া|Nazirpur:নাজিরপুর|Nesarabad (Swarupkathi):নেছারাবাদ (স্বরূপকাঠি)|Pirojpur Sadar:পিরোজপুর সদর"),

        // Sylhet Division
        "Habiganj" to parseUpazilas("Ajmiriganj:আজমিরীগঞ্জ|Bahubal:বাহুবল|Baniyachong:বানিয়াচং|Chunarughat:চুনারুঘাট|Habiganj Sadar:হবিগঞ্জ সদর|Lakhai:লাখাই|Madhabpur:মাধবপুর|Nabiganj:নবীগঞ্জ|Shayestaganj:শায়েস্তাগঞ্জ"),
        "Moulvibazar" to parseUpazilas("Barlekha:বড়লেখা|Juri:জুড়ী|Kamalganj:কমলগঞ্জ|Kulaura:কুলাউড়া|Moulvibazar Sadar:মৌলভীবাজার সদর|Rajnagar:রাজনগর|Sreemangal:শ্রীমঙ্গল"),
        "Sunamganj" to parseUpazilas("Bishwamvarpur:বিশ্বম্ভরপুর|Chhatak:ছাতক|Derai:দিরাই|Dharamapasha:ধর্মপাশা|Dowarabazar:দোয়ারাবাজার|Jagannathpur:জগন্নাথপুর|Jamalganj:জামালগঞ্জ|Madhyanagar:মধ্যনগর|Shantiganj:শান্তিগঞ্জ|Sullah:শাল্লা|Sunamganj Sadar:সুনামগঞ্জ সদর|Tahirpur:তাহিরপুর"),
        "Sylhet" to parseUpazilas("Balaganj:বালাগঞ্জ|Beanibazar:বিয়ানীবাজার|Bishwanath:বিশ্বনাথ|Companiganj:কোম্পানীগঞ্জ|Dakshin Surma:দক্ষিণ সুরমা|Fenchuganj:ফেঞ্চুগঞ্জ|Golapganj:গোলাপগঞ্জ|Gowainghat:গোয়াইনঘাট|Jaintiapur:জৈন্তাপুর|Kanaighat:কানাইঘাট|Osmani Nagar:ওসমানীনগর|Sylhet Sadar:সিলেট সদর|Zakiganj:জকিগঞ্জ"),

        // Rangpur Division
        "Dinajpur" to parseUpazilas("Birampur:বিরামপুর|Birganj:বীরগঞ্জ|Biral:বিরল|Bochaganj:বোচাগঞ্জ|Chirirbandar:চিরিরবন্দর|Dinajpur Sadar:দিনাজপুর সদর|Fulbari:ফুলবাড়ী|Ghoraghat:ঘোড়াঘাট|Hakimpur:হাকিমপুর|Kaharole:কাহারোল|Khansama:খানসামা|Nawabganj:নবাবগঞ্জ|Parbatipur:পার্বতীপুর"),
        "Gaibandha" to parseUpazilas("Fulchhari:ফুলছড়ি|Gaibandha Sadar:গাইবান্ধা সদর|Gobindaganj:গোবিন্দগঞ্জ|Palashbari:পলাশবাড়ী|Sadullapur:সাদুল্লাপুর|Saghata:সাঘাটা|Sundarganj:সুন্দরগঞ্জ"),
        "Kurigram" to parseUpazilas("Bhurungamari:ভুরুঙ্গামারী|Char Rajibpur:চর রাজিবপুর|Chilmari:চিলমারী|Kurigram Sadar:কুড়িগ্রাম সদর|Nageshwari:নাগেশ্বরী|Phulbari:ফুলবাড়ী|Rajarhat:রাজারহাট|Raomari:রৌমারী|Ulipur:উলিপুর"),
        "Lalmonirhat" to parseUpazilas("Aditmari:আদিতমারী|Hatibandha:হাতীবান্ধা|Kaliganj:কালীগঞ্জ|Lalmonirhat Sadar:লালমনিরহাট সদর|Patgram:পাটগ্রাম"),
        "Nilphamari" to parseUpazilas("Dimla:ডিমলা|Domar:ডোমার|Jaldhaka:জলঢাকা|Kishoreganj:কিশোরগঞ্জ|Nilphamari Sadar:নীলফামারী সদর|Saidpur:সৈয়দপুর"),
        "Panchagarh" to parseUpazilas("Atwari:আটোয়ারী|Boda:বোদা|Debiganj:দেবীগঞ্জ|Panchagarh Sadar:পঞ্চগড় সদর|Tetulia:তেঁতুলিয়া"),
        "Rangpur" to parseUpazilas("Badarganj:বদরগঞ্জ|Gangachara:গঙ্গাচড়া|Kaunia:কাউনিয়া|Mithapukur:মিঠাপুকুর|Pirgachha:পীরগাছা|Pirganj:পীরগঞ্জ|Rangpur Sadar:রংপুর সদর|Taraganj:তারাগঞ্জ"),
        "Thakurgaon" to parseUpazilas("Baliadangi:বালিয়াডাঙ্গী|Haripur:হরিপুর|Pirganj:পীরগঞ্জ|Ranisankail:রাণীশংকৈল|Thakurgaon Sadar:ঠাকুরগাঁও সদর"),

        // Mymensingh Division
        "Jamalpur" to parseUpazilas("Bakshiganj:বকশীগঞ্জ|Dewanganj:দেওয়ানগঞ্জ|Islampur:ইসলামপুর|Jamalpur Sadar:জামালপুর সদর|Madarganj:মাদারগঞ্জ|Melandaha:মেলান্দহ|Sarishabari:সরিষাবাড়ী"),
        "Mymensingh" to parseUpazilas("Bhaluka:ভালুকা|Dhobaura:ধোবাউড়া|Fulbaria:ফুলবাড়ীয়া|Gaffargaon:গফরগাঁও|Gauripur:গৌরীপুর|Haluaghat:হালুয়াঘাট|Ishwarganj:ঈশ্বরগঞ্জ|Muktagachha:মুক্তাগাছা|Mymensingh Sadar:ময়মনসিংহ সদর|Nandail:নান্দাইল|Phulpur:ফুলপুর|Tarakanda:তারাকান্দা|Trishal:ত্রিশাল"),
        "Netrokona" to parseUpazilas("Atpara:আটপাড়া|Barhatta:বারহাট্টা|Durgapur:দুর্গাপুর|Kalmakanda:কলমাকান্দা|Kendua:কেন্দুয়া|Khaliajuri:খালিয়াজুরী|Madan:মদন|Mohanganj:মোহনগঞ্জ|Netrokona Sadar:নেত্রকোণা সদর|Purbadhala:পূর্বধলা"),
        "Sherpur" to parseUpazilas("Jhenaigati:ঝিনাইগাতী|Nakla:নকলা|Nalitabari:নালিতাবাড়ী|Sherpur Sadar:শেরপুর সদর|Sreebardi:শ্রীবরদী")
    )

    fun districtsFor(divisionEn: String): List<LocationOption> =
        districtsByDivision[divisionEn].orEmpty()

    fun upazilasFor(districtEn: String): List<LocationOption> =
        upazilasByDistrict[districtEn].orEmpty()

    fun findDivision(divisionEn: String): LocationOption? =
        divisions.firstOrNull { it.nameEn.equals(divisionEn, ignoreCase = true) }

    fun findDistrict(divisionEn: String, districtEn: String): LocationOption? =
        districtsFor(divisionEn).firstOrNull { it.nameEn.equals(districtEn, ignoreCase = true) }

    fun findUpazila(districtEn: String, upazilaEn: String): LocationOption? =
        upazilasFor(districtEn).firstOrNull { it.nameEn.equals(upazilaEn, ignoreCase = true) }

    fun formatLocationSummary(
        divisionEn: String,
        districtEn: String,
        upazilaEn: String,
        language: AppLanguage
    ): String {
        val divLabel = findDivision(divisionEn)?.localized(language) ?: divisionEn
        val distLabel = findDistrict(divisionEn, districtEn)?.localized(language) ?: districtEn
        val upaLabel = findUpazila(districtEn, upazilaEn)?.localized(language) ?: upazilaEn
        return listOf(upaLabel, distLabel, divLabel)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }
}

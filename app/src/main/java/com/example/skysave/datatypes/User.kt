package com.example.skysave.datatypes

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required

open class User(
    @PrimaryKey var uid: String = "",
    @Required var email: String = "",
    @Required var alias: String = "",
    @Required var starred_files: RealmList<String> = RealmList()
) : RealmObject()
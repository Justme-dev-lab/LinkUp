package com.example.linkup.model

class Users
{
    private var uid: String = ""
    private var username: String = ""
    private var profile: String = ""
    private var search: String = ""

    constructor()
    constructor(uid: String, profile: String, username: String, search: String) {
        this.uid = uid
        this.profile = profile
        this.username = username
        this.search = search
    }
    fun getUID(): String?{
        return uid
    }

    fun setUID(uid: String){
        this.uid = uid

    }

    fun getUsername(): String?{
        return username
    }

    fun setUsername(username: String){
        this.username = username

    }

    fun getProfile(): String?{
        return profile
    }

    fun setProfile(profile: String){
        this.profile = profile

    }

    fun getSearch(): String?{
        return search
    }

    fun setSearch(search: String){
        this.search = search

    }


}
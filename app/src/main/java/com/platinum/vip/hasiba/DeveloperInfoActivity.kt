package com.platinum.vip.hasiba

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mikepenz.iconics.view.IconicsImageView

class DeveloperInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_info)

        // زر الإغلاق (سهم رجوع)
        val btnClose = findViewById<IconicsImageView>(R.id.btnCloseDev)
        btnClose.setOnClickListener { finish() }

        // زر فيسبوك
        val btnFacebook = findViewById<MaterialCardView>(R.id.btnFacebookInfo)
        btnFacebook.setOnClickListener {
            val facebookUrl = "https://www.facebook.com/obadakamal999"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("fb://facewebmodal/f?href=$facebookUrl"))
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(facebookUrl)))
            }
        }

        // زر سياسة الخصوصية
        val btnPrivacy = findViewById<MaterialCardView>(R.id.btnPrivacyPolicy)
        btnPrivacy.setOnClickListener {
            val privacyUrl = "https://obadaok.github.io/obada/privacy-policy.html"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
        }

        // زر الموقع الرسمي للمطور
        val btnDeveloperWebsite = findViewById<MaterialCardView>(R.id.btnDeveloperWebsite)
        btnDeveloperWebsite.setOnClickListener {
            val developerUrl = "https://bidoai.netlify.app/"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(developerUrl)))
        }

        // زر "عرض المزيد" لتوسيع النص
        val tvShort = findViewById<TextView>(R.id.tvShortDescription)
        val tvFull = findViewById<TextView>(R.id.tvFullDescription)
        val btnReadMore = findViewById<MaterialButton>(R.id.btnReadMore)

        btnReadMore.setOnClickListener {
            if (tvFull.visibility == TextView.GONE) {
                tvFull.visibility = TextView.VISIBLE
                btnReadMore.text = "عرض أقل"
            } else {
                tvFull.visibility = TextView.GONE
                btnReadMore.text = "عرض المزيد"
            }
        }
    }
}
package com.user.jarvis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlightTrackerActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyStateText: TextView
    private lateinit var retryButton: Button
    private lateinit var lastUpdatedText: TextView
    private lateinit var adapter: FlightAdapter

    private var allFlights = listOf<FlightItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_tracker)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.recyclerView)
        searchInput = findViewById(R.id.searchInput)
        emptyState = findViewById(R.id.emptyState)
        emptyStateText = findViewById(R.id.emptyStateText)
        retryButton = findViewById(R.id.retryButton)
        lastUpdatedText = findViewById(R.id.lastUpdatedText)

        findViewById<ImageView>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.refreshButton).setOnClickListener { fetchFlights(true) }
        retryButton.setOnClickListener { fetchFlights(true) }

        adapter = FlightAdapter(emptyList()) { flightNumber ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Flight Number", flightNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied $flightNumber", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { fetchFlights(true) }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterFlights(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val query = intent.getStringExtra("SEARCH_QUERY")
        if (!query.isNullOrBlank()) {
            searchInput.setText(query)
        }

        fetchFlights(false)
    }

    private fun fetchFlights(force: Boolean) {
        if (!force && FlightRepository.lastFetchedFlights.isNotEmpty()) {
            allFlights = FlightRepository.lastFetchedFlights
            updateLastUpdatedTime(FlightRepository.lastUpdateTime)
            filterFlights(searchInput.text.toString())
            return
        }

        swipeRefresh.isRefreshing = true
        emptyState.visibility = View.GONE
        retryButton.visibility = View.GONE

        FlightRepository.fetchFlights(this,
            onSuccess = { flights ->
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    allFlights = flights
                    updateLastUpdatedTime(FlightRepository.lastUpdateTime)
                    filterFlights(searchInput.text.toString())
                }
            },
            onError = { error ->
                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    if (allFlights.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        emptyStateText.text = error
                        retryButton.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                }
            }
        )
    }

    private fun updateLastUpdatedTime(timestamp: Double) {
        if (timestamp > 0) {
            val date = Date((timestamp * 1000).toLong())
            val format = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())
            lastUpdatedText.text = "Last updated: ${format.format(date)}"
        } else {
            lastUpdatedText.text = "Updated just now"
        }
    }

    private fun filterFlights(query: String) {
        if (query.isBlank()) {
            adapter.updateData(allFlights)
        } else {
            val q = query.lowercase(Locale.getDefault())
            val filtered = allFlights.filter {
                it.flight.lowercase().contains(q) ||
                it.callsign.lowercase().contains(q) ||
                it.model.lowercase().contains(q) ||
                it.type.lowercase().contains(q)
            }
            adapter.updateData(filtered)
        }

        if (adapter.itemCount == 0) {
            emptyState.visibility = View.VISIBLE
            emptyStateText.text = if (allFlights.isEmpty()) "No flights tracked right now" else "No matching flights found"
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    inner class FlightAdapter(
        private var flights: List<FlightItem>,
        private val onCopyClick: (String) -> Unit
    ) : RecyclerView.Adapter<FlightAdapter.FlightViewHolder>() {

        inner class FlightViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val flightNumberText: TextView = view.findViewById(R.id.flightNumberText)
            val copyButton: ImageView = view.findViewById(R.id.copyButton)
            val fromIataText: TextView = view.findViewById(R.id.fromIataText)
            val fromCityText: TextView = view.findViewById(R.id.fromCityText)
            val toIataText: TextView = view.findViewById(R.id.toIataText)
            val toCityText: TextView = view.findViewById(R.id.toCityText)
            val aircraftText: TextView = view.findViewById(R.id.aircraftText)
            val callsignText: TextView = view.findViewById(R.id.callsignText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlightViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_flight_card, parent, false)
            return FlightViewHolder(view)
        }

        override fun onBindViewHolder(holder: FlightViewHolder, position: Int) {
            val flight = flights[position]

            holder.flightNumberText.text = flight.flight.ifBlank { "Unknown" }

            holder.fromIataText.text = if (flight.fromIata.isNotBlank() && flight.fromIata != "null") flight.fromIata else "UNK"
            holder.fromCityText.text = if (flight.fromCity.isNotBlank() && flight.fromCity != "null") flight.fromCity else "Unknown"

            holder.toIataText.text = if (flight.toIata.isNotBlank() && flight.toIata != "null") flight.toIata else "UNK"
            holder.toCityText.text = if (flight.toCity.isNotBlank() && flight.toCity != "null") flight.toCity else "Unknown"

            holder.aircraftText.text = "${flight.model} (${flight.type})"
            holder.callsignText.text = "${flight.callsign} • ${flight.clicks} views"

            holder.copyButton.setOnClickListener {
                onCopyClick(flight.flight)
            }
        }

        override fun getItemCount() = flights.size

        fun updateData(newFlights: List<FlightItem>) {
            flights = newFlights
            notifyDataSetChanged()
        }
    }
}

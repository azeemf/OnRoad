package com.onroad.app.ui.search

import com.onroad.app.data.model.directions.DirectionsResponse
import com.onroad.app.data.provider.DataManager
import com.onroad.app.service.ApiService
import com.onroad.app.ui.base.BaseCallbackInteractor
import com.onroad.app.ui.base.BaseCallbackPresenter
import com.onroad.app.ui.base.BaseInteractor
import javax.inject.Inject

class SearchInteractor @Inject constructor(dataManager: DataManager, service: ApiService) : BaseInteractor(dataManager, service), SearchContract.Interactor {

    override fun getTrack(source: String, destiny: String, apiKey: String, callback: BaseCallbackPresenter<DirectionsResponse>) {
        service.getDirections(source, destiny, apiKey).enqueue(object : BaseCallbackInteractor<DirectionsResponse>(callback) {
            override fun onSuccess(response: DirectionsResponse) {
                callback.then(response)
            }
        })
    }
}
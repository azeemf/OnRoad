package com.onroad.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.onroad.app.databinding.FragmentHomeBinding
import com.onroad.app.ui.base.BaseFragment
import com.onroad.app.ui.search.SearchActivity
import com.onroad.app.util.goToActivity
import javax.inject.Inject


class HomeFragment : BaseFragment(), HomeContract.View {

    @Inject
    lateinit var presenter: HomeContract.Presenter<HomeContract.View>;

    //view que contiene los elementos de este fragment
    lateinit var binding: FragmentHomeBinding;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        baseActivity.activityComponent.inject(this);
        presenter.attachFragment(this, baseActivity);
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        //si es la primera vez que se accede al fragment lo creamos
        if (!::binding.isInitialized) {
            binding = FragmentHomeBinding.inflate(layoutInflater, container, false);
            initFragment();
            //si no es que el fragment ya estaba inicializado
        }

        return binding.root;
    }

    override fun initFragment() {
        binding.apply {
            exploreButton.setOnClickListener {
                goToActivity(baseActivity, SearchActivity::class.java)
            }
        }
    }

    fun isBindingInitialized() = ::binding.isInitialized;
}